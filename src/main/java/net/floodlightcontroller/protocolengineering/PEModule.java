package net.floodlightcontroller.protocolengineering;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.protocolengineering.emergency.IPESubmodule;
import net.floodlightcontroller.protocolengineering.linkfailure.LinkFailureHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PEModule implements IFloodlightModule {

	private static final Logger log = LoggerFactory.getLogger(PEModule.class);

	private final List<IPESubmodule> submodules = Arrays.asList(
			//new EmergencyHandler(),
			new LinkFailureHandler()
	);

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		return submodules.stream()
				.map(IPESubmodule::getModuleDependencies)
				.flatMap(Collection::stream)
				.distinct()
				.collect(Collectors.toList());
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		log.info("Initialized " + getClass().getName());
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		log.info("Startup " + getClass().getName());
		submodules.forEach(m -> {
			m.startUp(context);
			log.info("Started " + m.getClass()
					.getName());
		});
	}
}
