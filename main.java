import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

//sections of the API's and JSON elements were found online
public class main {

    private static final String NOMINATIM_API = "https://nominatim.openstreetmap.org/search?format=json&q=";
    private static final String NOAA_API = "https://api.weather.gov/points/";
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // user input
        System.out.println("enter the address (e.g. 1500 greenbriar blvd, Boulder, CO): ");
        String address = scanner.nextLine();
        System.out.println("enter snowfall threshold in inches (e.g. 5.0): ");
        double threshold = scanner.nextDouble();

        try {
            // Convert address to coordinates
            double[] coordinates = getCoordinatesFromAddress(address);
            if (coordinates == null) {
                System.out.println("could not calculate coordinates");
                return;
            }
            double latitude = Math.round(coordinates[0] * 10000.0) / 10000.0;
            double longitude = Math.round(coordinates[1] * 10000.0) / 10000.0;

            // forecast for location
            WeatherData data = getForecast(latitude, longitude);
            if (data != null) {
                System.out.println("\nWeather forecast:");
                System.out.println("Short forecast: " + data.shortForecast);
                System.out.println("Temperature: " + data.temperature);
                System.out.println("Wind speed: " + data.windSpeed);
                System.out.println("Snowfall amount: " + data.snowfallAmount + " inches");

                if (data.snowfallAmount >= threshold) {
                    System.out.println("snowfall exceeds the threshold of " + threshold + " inches");
                } else {
                    System.out.println("snowfall does NOT exceed the threshold of " + threshold + " inches");
                }
            } else {
                System.out.println("could not retrieve weather forecast");
            }

        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
        } finally {
            scanner.close();
        }
    }

    // converts address to latitude and longitude
    // parts of this code was found online ->
    private static double[] getCoordinatesFromAddress(String address) throws Exception {
        String url = NOMINATIM_API + address.replace(" ", "+");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Main/1.0")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONArray jsonArray = new JSONArray(response.body());

        if (jsonArray.length() == 0) return null;

        JSONObject location = jsonArray.getJSONObject(0);
        double lat = location.getDouble("lat");
        double lon = location.getDouble("lon");
        return new double[]{lat, lon};
    }
    //<-end
    // gets the first forecast period and weather details
    private static WeatherData getForecast(double latitude, double longitude) throws Exception {
        String pointUrl = NOAA_API + latitude + "," + longitude;
        HttpRequest pointRequest = HttpRequest.newBuilder()
                .uri(URI.create(pointUrl))
                .header("User-Agent", "Main/1.0")
                .GET()
                .build();

        HttpResponse<String> pointResponse = client.send(pointRequest, HttpResponse.BodyHandlers.ofString());
        JSONObject pointJson = new JSONObject(pointResponse.body());
        String forecastUrl = pointJson.getJSONObject("properties").getString("forecast");

        HttpRequest forecastRequest = HttpRequest.newBuilder()
                .uri(URI.create(forecastUrl))
                .header("User-Agent", "Main/1.0")
                .GET()
                .build();

        HttpResponse<String> forecastResponse = client.send(forecastRequest, HttpResponse.BodyHandlers.ofString());
        JSONObject forecastJson = new JSONObject(forecastResponse.body());
        JSONArray periods = forecastJson.getJSONObject("properties").getJSONArray("periods");

        // use the first forecast period
        if (periods.length() > 0) {
            JSONObject period = periods.getJSONObject(0);
            String shortForecast = period.getString("shortForecast");
            String detailedForecast = period.getString("detailedForecast").toLowerCase();
            String temperature = period.getInt("temperature") + " " + period.getString("temperatureUnit");
            String windSpeed = period.getString("windSpeed");

            double snowfallAmount = detailedForecast.contains("snow") ? extractSnowfallAmount(detailedForecast) : 0.0;

            return new WeatherData(snowfallAmount, temperature, shortForecast, windSpeed);
        }
        return null;
    }

    // extract snowfall amount from text
    private static double extractSnowfallAmount(String forecast) {
        try {
            String[] parts = forecast.split(" ");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals("to") && i > 0 && i + 1 < parts.length) {
                    return Double.parseDouble(parts[i + 1]);
                } else if (parts[i].contains("inch") && i > 0) {
                    return Double.parseDouble(parts[i - 1]);
                }
            }
        } catch (NumberFormatException e) {
            // ignores errors
        }
        return 0.0;
    }

    // class to hold weather data
    private static class WeatherData {
        double snowfallAmount;
        String temperature;
        String shortForecast;
        String windSpeed;

        WeatherData(double snowfallAmount, String temperature, String shortForecast, String windSpeed) {
            this.snowfallAmount = snowfallAmount;
            this.temperature = temperature;
            this.shortForecast = shortForecast;
            this.windSpeed = windSpeed;
        }
    }
}
