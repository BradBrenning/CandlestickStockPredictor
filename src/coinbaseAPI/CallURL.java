package coinbaseAPI;

import java.net.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class CallURL {

	int responseCode;
	String body;

	// Constructor
	public CallURL () {
		responseCode = 0;
		body = null;
	}

	// Calls API and retrieves data from API
	public boolean call (String urlString) {
       services.Logger.log("CallURL.call: " + urlString);
		try {
            URL url = URI.create(urlString).toURL();
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");

			try (BufferedReader reader = new BufferedReader(
				 new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)))
			{
                responseCode = connection.getResponseCode();
				StringBuilder response = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					response.append(line).append("\n");
				}
				body = response.toString();
               services.Logger.log("CallURL.call response code=" + responseCode + " bodyLen=" + (body == null ? 0 : body.length()));
			}
		}
		catch (IllegalArgumentException | IOException e) {
			//System.out.println(e);
			return false;
		}
		return true;
	}

	// Used to return error code sent back from API
	public int getResponseCode() {
		return responseCode;
	}

	// Used to return response send back from API
	public String getBody() {
		return body;
	}
}