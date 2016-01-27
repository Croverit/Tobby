package messages.connection;

import messages.Message;
import utilities.Log;

public class IdentificationFailedMessage extends Message {
	
	public IdentificationFailedMessage(Message msg) {
		super(msg);
		deserialize();
	}

	public void deserialize() {
		byte reason = this.content[0];
		Log.p("Authentification failed for reason " + reason);
	}
}
