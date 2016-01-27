package messages.maps;

import java.util.Vector;

import utilities.ByteArray;

public class FightTeamInformations extends AbstractFightTeamInformations {
    public Vector<FightTeamMemberInformations> teamMembers;

	public FightTeamInformations(ByteArray buffer) {
		super(buffer);
		this.teamMembers = new Vector<FightTeamMemberInformations>();
		int nb = buffer.readShort();
		for(int i = 0; i < nb; ++i) {
			buffer.readShort(); // id du message FightTeamMemberInformations
			this.teamMembers.add(new FightTeamMemberInformations(buffer));
		}
	}
}