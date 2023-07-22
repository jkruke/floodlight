package net.floodlightcontroller.protocolengineering.emergency;

import java.util.Collection;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightService;

public interface IPESubmodule {

	void startUp(FloodlightModuleContext context);

	Collection<Class<? extends IFloodlightService>> getModuleDependencies();

}
