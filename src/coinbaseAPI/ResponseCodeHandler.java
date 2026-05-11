package coinbaseAPI;

import java.util.concurrent.TimeUnit;

public class ResponseCodeHandler {

	public String codeResponse (int response) {
		switch (response) {
			case 200:
				return "Continue";
			case 429:
				System.out.println("Sleeping");
				try
				{
					TimeUnit.SECONDS.sleep(1);
				}
				catch (InterruptedException e) {
					//continue
				}
				System.out.println("Waking up");
				return "Try Again";
			default:
				return "Not configured " + response;
		}
	}
}