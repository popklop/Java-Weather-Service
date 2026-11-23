
package org.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Main {

    private static final String API_URL =
            "https://api.weather.yandex.ru/v2/forecast";

    private static final String API_KEY_ENV =
            "YANDEX_WEATHER_API_KEY";

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper();

    public static void main(String[] args) throws IOException, InterruptedException {
        String apiKey = System.getenv(API_KEY_ENV);

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Не задан API-ключ. Установите переменную окружения "
                            + API_KEY_ENV
            );
        }
        double latitude = args.length > 0
                ? Double.parseDouble(args[0])
                : 55.75;

        double longitude = args.length > 1
                ? Double.parseDouble(args[1])
                : 37.62;

        int limit = args.length > 2
                ? Integer.parseInt(args[2])
                : 5;

        validateParameters(latitude, longitude, limit);
        URI uri = URI.create(
                API_URL
                        + "?lat=" + latitude
                        + "&lon=" + longitude
                        + "&limit=" + limit
                        + "&lang=ru_RU"
        );
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(20))
                .header("X-Yandex-Weather-Key", apiKey)
                .GET()
                .build();
        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        String responseBody = response.body();
        if (response.statusCode() != 200) {
            System.err.println(
                    "Ошибка при получении погоды. HTTP-код: "
                            + response.statusCode()
            );

            System.err.println("Ответ сервера:");
            System.err.println(responseBody);

            return;
        }
        System.out.println("========== ПОЛНЫЙ JSON ОТВЕТ ==========");
        System.out.println(responseBody);
        JsonNode root = OBJECT_MAPPER.readTree(responseBody);
        printCurrentTemperature(root);
        calculateAverageTemperature(root, limit);
    }

    private static void validateParameters(
            double latitude,
            double longitude,
            int limit
    ) {
        if (!Double.isFinite(latitude)
                || latitude < -90
                || latitude > 90) {
            throw new IllegalArgumentException(
                    "Широта должна находиться в диапазоне от -90 до 90"
            );
        }

        if (!Double.isFinite(longitude)
                || longitude < -180
                || longitude > 180) {
            throw new IllegalArgumentException(
                    "Долгота должна находиться в диапазоне от -180 до 180"
            );
        }

        if (limit < 1 || limit > 7) {
            throw new IllegalArgumentException(
                    "Количество дней должно быть от 1 до 7"
            );
        }
    }
    private static void printCurrentTemperature(JsonNode root) {

        JsonNode temperature = root.path("fact").path("temp");

        if (!temperature.isNumber()) {
            throw new IllegalStateException(
                    "В ответе API отсутствует поле fact.temp"
            );
        }

        System.out.println("\n========== ТЕКУЩАЯ ПОГОДА ==========");

        System.out.printf(
                "Текущая температура: %.1f °C%n",
                temperature.asDouble()
        );
    }
    private static void calculateAverageTemperature(
            JsonNode root,
            int limit
    ) {

        JsonNode forecasts = root.path("forecasts");

        if (!forecasts.isArray()) {
            throw new IllegalStateException(
                    "В ответе API отсутствует массив forecasts"
            );
        }

        if (forecasts.isEmpty()) {
            throw new IllegalStateException(
                    "API не вернул прогноз погоды"
            );
        }
        int days = Math.min(limit, forecasts.size());

        double temperatureSum = 0;

        System.out.println("\n========== ПРОГНОЗ ПО ДНЯМ ==========");

        for (int i = 0; i < days; i++) {

            JsonNode forecast = forecasts.get(i);
            JsonNode averageTemperature = forecast
                    .path("parts")
                    .path("day")
                    .path("temp_avg");

            if (!averageTemperature.isNumber()) {
                throw new IllegalStateException(
                        "Не удалось получить parts.day.temp_avg "
                                + "для дня " + forecast.path("date").asText()
                );
            }

            double temperature = averageTemperature.asDouble();

            temperatureSum += temperature;

            String date = forecast.path("date").asText();

            System.out.printf(
                    "%s: %.1f °C%n",
                    date,
                    temperature
            );
        }
        double averageTemperature = temperatureSum / days;

        System.out.println("\n========== РЕЗУЛЬТАТ ==========");

        System.out.printf(
                "Средняя температура за %d дней: %.2f °C%n",
                days,
                averageTemperature
        );
    }
}
