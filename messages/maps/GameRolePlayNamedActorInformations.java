package messages.maps;

import utilities.ByteArray;

public class GameRolePlayNamedActorInformations extends GameRolePlayActorInformations {
	public String name = "";
	
	public GameRolePlayNamedActorInformations(ByteArray buffer) {
		super(buffer);
		this.name = buffer.readUTF();
	}
}
