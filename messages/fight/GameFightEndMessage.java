package messages.fight;

import messages.Message;

public class GameFightEndMessage extends Message{
	/*

    public int duration = 0;
    
    public int ageBonus = 0;
    
    public int lootShareLimitMalus = 0;
    
    public Vector<FightResultListEntry> results;
    
    public Vector<NamedPartyTeamWithOutcome> namedPartyTeamsOutcomes;
    
    public GameFightEndMessage(Message msg)
    {
       super(msg);
       this.results = new Vector.<FightResultListEntry>();
       this.namedPartyTeamsOutcomes = new Vector.<NamedPartyTeamWithOutcome>();
    }
    
    
    public void deserialize(ByteArray buffer)
    {
       int loc6 = 0;
       var _loc7_:FightResultListEntry = null;
       var _loc8_:NamedPartyTeamWithOutcome = null;
       this.ageBonus = buffer.readShort();
       this.lootShareLimitMalus = buffer.readShort();
       var _loc2_:uint = buffer.readUnsignedShort();
       var _loc3_:uint = 0;
       while(_loc3_ < _loc2_)
       {
          _loc6_ = buffer.readUnsignedShort();
          _loc7_ = ProtocolTypeManager.getInstance(FightResultListEntry,_loc6_);
          _loc7_.deserialize(buffer);
          this.results.push(_loc7_);
          _loc3_++;
       }
       var _loc4_:uint = buffer.readUnsignedShort();
       var _loc5_:uint = 0;
       while(_loc5_ < _loc4_)
       {
          _loc8_ = new NamedPartyTeamWithOutcome();
          _loc8_.deserialize(buffer);
          this.namedPartyTeamsOutcomes.push(_loc8_);
          _loc5_++;
       }
    }
    */
}
