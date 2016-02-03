package roleplay.fight;

import roleplay.currentmap.ActorAlignmentInformations;
import utilities.ByteArray;

public class GameFightCharacterInformations extends GameFightFighterNamedInformations{

    public int level = 0;
    
    public ActorAlignmentInformations alignmentInfos;
    
    public int breed = 0;
    
    public boolean sex = false;
    
    public GameFightCharacterInformations(ByteArray buffer)
    {
    	super(buffer);
    }
    
    public void deserialize(ByteArray buffer)
    {
       super.deserialize(buffer);
       this.level = buffer.readByte();
       if(this.level < 0 || this.level > 255)
       {
          throw new Error("Forbidden value (" + this.level + ") on element of GameFightCharacterInformations.level.");
       }
       this.alignmentInfos = new ActorAlignmentInformations(buffer);
       this.breed = buffer.readByte();
       this.sex = buffer.readBoolean();
    }
}