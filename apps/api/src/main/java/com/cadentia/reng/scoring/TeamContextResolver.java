package com.cadentia.reng.scoring;

import com.cadentia.reng.scoring.TeamSuitabilityModels.ExplicitTeamConstraints;
import com.cadentia.reng.scoring.TeamSuitabilityModels.TeamContextReference;

public interface TeamContextResolver {

    ExplicitTeamConstraints resolve(TeamContextReference reference);
}
