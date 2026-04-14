package com.rods.backtestingstrategies;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import yahoofinance.YahooFinance;
import yahoofinance.Stock;

public class TestYahooCrumb {
    public static void main(String[] args) {
        try {
            System.setProperty("http.agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36");

            CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            java.net.CookieHandler.setDefault(cookieManager);

            HttpClient client = HttpClient.newBuilder()
                    .cookieHandler(cookieManager)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            // 1. Get Cookie
            HttpRequest req1 = HttpRequest.newBuilder()
                    .uri(URI.create("https://fc.yahoo.com"))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .GET()
                    .build();
            client.send(req1, HttpResponse.BodyHandlers.discarding());
            System.out.println("Got cookies: " + cookieManager.getCookieStore().getCookies());

            // 2. Get Crumb
            HttpRequest req2 = HttpRequest.newBuilder()
                    .uri(URI.create("https://query1.finance.yahoo.com/v1/test/getcrumb"))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .GET()
                    .build();
            HttpResponse<String> res2 = client.send(req2, HttpResponse.BodyHandlers.ofString());
            String crumb = res2.body();
            System.out.println("Got crumb: " + crumb);

            // Set crumb internally for yahoofinance-api if possible, using reflection if
            // needed
            Class.forName("yahoofinance.histquotes2.CrumbManager").getDeclaredMethod("setCrumb", String.class)
                    .invoke(null, crumb);

            Stock stock = YahooFinance.get("AAPL");
            System.out.println("Success! Price: " + stock.getQuote().getPrice());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
