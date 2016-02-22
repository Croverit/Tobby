package game.currentmap;

import game.ProtocolTypeManager;
import utilities.ByteArray;

public class GameRolePlayTaxCollectorInformations extends GameRolePlayActorInformations{

	public static final int  protocolId = 148;
    
    public TaxCollectorStaticInformations identification;
    
    public int guildLevel = 0;
    
    public int taxCollectorAttack = 0;
    
    public GameRolePlayTaxCollectorInformations(ByteArray buffer)
    {
    	super(buffer);
        this.identification = (TaxCollectorStaticInformations) ProtocolTypeManager.getInstance(buffer.readShort(), buffer);
        this.guildLevel = buffer.readByte();
        this.taxCollectorAttack = buffer.readInt();
    }
    
 
}
