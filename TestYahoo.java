import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import java.io.IOException;

public class TestYahoo {
    public static void main(String[] args) throws IOException {
        System.setProperty("http.agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36");
        try {
            Stock stock = YahooFinance.get("AAPL");
            System.out.println("Success! Price: " + stock.getQuote().getPrice());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
