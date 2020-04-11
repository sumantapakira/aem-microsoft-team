package com.aem.miscosoft.core.listeners;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class AzureAD {

	private static final Logger LOG = LoggerFactory.getLogger(JcrSysEnvChangeListener.class);
	private static final String URL ="https://outlook.office.com/webhook/4c62778b-9828-49e4-97e5-6ddf77ba77f5@86194bee-e3f4-49e4-a570-54ed6c5bd46b/IncomingWebhook/66509f1c43c54674842cb8adadf47ecc/f59959c9-b197-4698-b127-6741c35ba01e"; 

	public String getAccessToken(String tenantId, String clientId, String clientSecret)
			throws MalformedURLException, IOException {
		String endpoint = String.format("https://login.microsoftonline.com/%s/oauth2/v2.0/token", tenantId);
		String postBody = String.format("grant_type=client_credentials&scope=https://graph.microsoft.com//.default&client_id=%s&client_secret=%s",
				clientId, clientSecret);
		HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
		conn.setRequestMethod("POST");
		conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		conn.setDoOutput(true);
		conn.getOutputStream().write(postBody.getBytes());
		conn.connect();
		JsonFactory factory = new JsonFactory();
		JsonParser parser = factory.createParser(conn.getInputStream());
		String accessToken = null;
		while (parser.nextToken() != JsonToken.END_OBJECT) {
			String name = parser.getCurrentName();
			if ("access_token".equals(name)) {
				parser.nextToken();
				accessToken = parser.getText();
			}
			if ("token_type".equals(name)) {
				parser.nextToken();
				LOG.debug("Token type : " + parser.getText());
			}
		}
		return accessToken;
	}



	public  String postChangeToTeamChannel(String accessToken, String  envName, String username, String packageName) throws IOException, JSONException {

		URL url = new URL(URL);

		String text = String.format("Deployment of the Package **%s**  has been completed by %s !",packageName, username);
		JSONObject mainobj = new JSONObject();
		mainobj.put("@context", "https://schema.org/extensions");
		mainobj.put("@type", "MessageCard");
		mainobj.put("themeColor", "0072C6");
		mainobj.put("title", envName + " Server");

		mainobj.put("text", text);

		LOG.debug(mainobj.toString());
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		conn.setRequestMethod("POST");
		conn.setRequestProperty("Authorization", "Bearer " + accessToken);
		conn.setRequestProperty("Accept","application/json");
		conn.addRequestProperty("Content-Type", "application/json");
		conn.setDoOutput(true);
		String postBody = mainobj.toString();
		conn.getOutputStream().write(postBody.getBytes());

		int httpResponseCode = conn.getResponseCode();
		if(httpResponseCode == 200) {
			BufferedReader in = null;
			StringBuilder response;
			try{
				in = new BufferedReader(
						new InputStreamReader(conn.getInputStream()));
				String inputLine;
				response = new StringBuilder();
				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
			} finally {
				in.close();
			}
			return response.toString();
		} else {
			return String.format("Connection returned HTTP code: %s with message: %s",
					httpResponseCode, conn.getResponseMessage());
		}
	}


}
