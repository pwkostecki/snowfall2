import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

public class main {

    private static final String NOMINATIM_API = "https://nominatim.openstreetmap.org/search?format=json&q=";
    private static final String NOAA_API = "https://api.weather.gov/points/";
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // get user input
        System.out.println("enter the address (ex street address(1500 greenbriar blvd), city(Boulder), state(CO): ");
        String address = scanner.nextLine();
        System.out.println("enter snowfall threshold in inches (ex 5.0): ");
        double threshold = scanner.nextDouble();

        try {
            // step 1: convert address to coordinates
            double[] coordinates = getCoordinatesFromAddress(address);
            if (coordinates == null) {
                System.out.println("could not calculate coordinates");
                return;
            }
            double latitude = coordinates[0];
            double longitude = coordinates[1];
            System.out.println("cordinates: " + latitude + ", " + longitude);

            // step 2: check snowfall at the location
            boolean hasSnowfall = checkSnowfall(latitude, longitude, threshold);
            System.out.println("snowfall exceeds " + threshold + " inches: " + hasSnowfall);

        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
        } finally {
            scanner.close();
        }
    }

    // convert address to lat and long
    // parts of the following code was found online
    private static double[] getCoordinatesFromAddress(String address) throws Exception {
        String url = NOMINATIM_API + address.replace(" ", "+");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Main/1.0") // REQUIRED (by api)
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


    // end online portion
    // NOAA api
    private static boolean checkSnowfall(double latitude, double longitude, double threshold) throws Exception {
        // step 1: get the forecast endpoint from the points API
        String pointUrl = NOAA_API + latitude + "," + longitude;
        HttpRequest pointRequest = HttpRequest.newBuilder()
                .uri(URI.create(pointUrl))
                .header("User-Agent", "Main/1.0") // user agent
                .GET()
                .build();

        HttpResponse<String> pointResponse = client.send(pointRequest, HttpResponse.BodyHandlers.ofString());
        JSONObject pointJson = new JSONObject(pointResponse.body());
        String forecastUrl = pointJson.getJSONObject("properties").getString("forecast");

        // step 2: fetch forecast data
        HttpRequest forecastRequest = HttpRequest.newBuilder()
                .uri(URI.create(forecastUrl))
                .header("User-Agent", "Main/1.0")
                .GET()
                .build();

        HttpResponse<String> forecastResponse = client.send(forecastRequest, HttpResponse.BodyHandlers.ofString());
        JSONObject forecastJson = new JSONObject(forecastResponse.body());
        JSONArray periods = forecastJson.getJSONObject("properties").getJSONArray("periods");

        // step 3: check each forecast period for snowfall
        for (int i = 0; i < periods.length(); i++) {
            JSONObject period = periods.getJSONObject(i);
            String detailedForecast = period.getString("detailedForecast").toLowerCase();

            // snowfall "mentions"
            if (detailedForecast.contains("snow")) {
                double snowfallAmount = extractSnowfallAmount(detailedForecast);
                if (snowfallAmount >= threshold) {
                    return true; // snowfall exceeds threshold
                }
            }
        }
        return false; // no snowfall exceeds threshold
    }

    private static double extractSnowfallAmount(String forecast) {
        try {
            // extracts max value from range (ex 1-3)
            String[] parts = forecast.split(" ");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals("to") && i > 0 && i + 1 < parts.length) {
                    return Double.parseDouble(parts[i + 1]); // end of range
                } else if (parts[i].contains("inch") && i > 0) {
                    return Double.parseDouble(parts[i - 1]); // singel value sisuation/case
                }
            }
        } catch (NumberFormatException e) {
            // fallback exception
        }
        return 0.0; // default
    }
}
