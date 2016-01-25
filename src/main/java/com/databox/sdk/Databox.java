package com.databox.sdk;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Databox {
  static final Logger logger = LoggerFactory.getLogger(Databox.class);
  private static final String DEFAULT_HOST = "https://push2new.databox.com";
  private static final String CLIENT_VERSION = "2.1";

  private final String _token;
  private String _host;

  {
    _host = DEFAULT_HOST;
  }

  public Databox(String token) {
    if (token == null || token.isEmpty()) {
      throw new RuntimeException("Databox token cannot be empty!");
    }
    _token = token;
  }

  public void push(String key, Double value) {
    push(key, value, null, null);
  }

  public void push(String key, Double value, Date date) {
    push(key, value, date, null);
  }

  public void push(String key, Double value, Map<String, Object> attributes) {
    push(key, value, null, attributes);
  }

  public void push(String key, Double value, Date date, Map<String, Object> attributes) {
    if (key == null || value == null) {
      logger.error("Key and value must not be null ({}, {})", key, value);
      return;
    }

    final KPI kpi = new KPI().setKey(key).setValue(value).setDate(date);
    if (attributes != null) {
      kpi.addAttributes(attributes);
    }
    push(Arrays.asList(kpi));
  }

  public boolean push(List<KPI> kpis) {
    if (kpis != null && !kpis.isEmpty()) {
      String rawData = "{\"data\": " + kpis.toString() + "}";
      return push(rawData);
    }
    return false;
  }

  private boolean push(String rawData) {
    //TODO: This must be overwritten. No time was scheduled to do this. Sorry. ;( Check 'post' and 'get' methods
    HttpURLConnection conn = null;
    OutputStream os = null;
    try {
      URL url = new URL(_host);
      conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setRequestProperty("User-Agent", "Databox/" + CLIENT_VERSION + " (Java)");
      String encodedToken = new String(Base64.getEncoder().encode((_token + ": ").getBytes("UTF-8")));
      conn.setRequestProperty("Authorization", "Basic " + encodedToken);

      conn.setDoOutput(true);
      conn.setDoInput(true);
      conn.setConnectTimeout(5000);

      DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
      wr.write(rawData.getBytes("UTF-8"));
      wr.flush();
      wr.close();

      InputStream inputStream;
      if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
        inputStream = conn.getInputStream();
      } else {
        inputStream = conn.getErrorStream();
      }
      BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));

      StringBuffer input = new StringBuffer();
      String in;
      while ((in = br.readLine()) != null) {
        input.append(in);
      }
      br.close();
      logger.info(input.toString() + ": " + rawData);
      return true;
    } catch (Exception e) {
      logger.error(e.getLocalizedMessage(), e);
      throw new RuntimeException(e);
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
      if (os != null) {
        try {
          os.close();
        } catch (IOException e) {
          logger.error(e.getLocalizedMessage(), e);
        }
      }
    }
  }

  private HttpURLConnection buildConnection(String method, String path) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) (new URL(_host + path)).openConnection();
    String encodedToken = new String(Base64.getEncoder().encode((_token + ": ").getBytes("UTF-8")));
    connection.setRequestProperty("Authorization", "Basic " + encodedToken);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("User-Agent", "Databox/" + CLIENT_VERSION + " (Java)");
    connection.setRequestMethod(method);
    connection.setDoOutput(true);
    connection.setDoInput(true);
    connection.setConnectTimeout(5000);
    return connection;
  }

  private StringBuffer lastResponse = null;
  private int lastResponseCode = -1;

  private StringBuffer handleResponseInputStream(InputStream inputStream) throws IOException {
    StringBuffer stringBuffer = new StringBuffer();
    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
      for (int c; (c = bufferedReader.read()) != -1; ) stringBuffer.append((char) c);
    }

    lastResponse = stringBuffer;
    return stringBuffer;
  }

  private StringBuffer get(String path) throws IOException {
    HttpURLConnection connection = buildConnection("GET", path);
    connection.connect();

    int responseCode = connection.getResponseCode();

    StringBuffer responseAsString = handleResponseInputStream(
      (responseCode >= 200 && responseCode < 300) ? connection.getInputStream() : connection.getErrorStream());

    lastResponseCode = responseCode;

    connection.disconnect();
    return responseAsString;
  }

  private StringBuffer post(String path, String rawData) throws IOException {
    HttpURLConnection connection = buildConnection("POST", path);
    connection.connect();

    int responseCode = connection.getResponseCode();

    DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());
    dataOutputStream.write(rawData.getBytes("UTF-8"));
    dataOutputStream.flush();
    dataOutputStream.close();

    StringBuffer responseAsString = handleResponseInputStream(
      (responseCode >= 200 && responseCode < 300) ? connection.getInputStream() : connection.getErrorStream());

    lastResponseCode = responseCode;

    connection.disconnect();
    return responseAsString;
  }

  public StringBuffer lastPushes(int n) throws IOException {
    return get("/lastpushes/" + (n + ""));
  }

  public StringBuffer lastPush() throws IOException {
    return lastPushes(1);
  }
}
