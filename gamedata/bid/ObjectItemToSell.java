package gamedata.bid;

import gamedata.ProtocolTypeManager;
import gamedata.inventory.Item;
import gamedata.inventory.ObjectEffect;

import utilities.ByteArray;

public class ObjectItemToSell extends Item {
	public int objectGID = 0;
	public ObjectEffect[] effects;
	public int objectUID = 0;
	public int quantity = 0;
	public int objectPrice = 0;
	
	public ObjectItemToSell(ByteArray buffer) {
		super(buffer);
		this.objectGID = buffer.readVarShort();
		int nb = buffer.readShort();
		this.effects = new ObjectEffect[nb];
		for(int i = 0; i < nb; ++i)
			this.effects[i] = (ObjectEffect) ProtocolTypeManager.getInstance(buffer.readShort(), buffer);
		this.objectUID = buffer.readVarInt();
        this.quantity = buffer.readVarInt();
        this.objectPrice = buffer.readVarInt();
	}
}