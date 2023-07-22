package net.floodlightcontroller.protocolengineering.linkfailure;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.protocolengineering.emergency.IPESubmodule;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinkFailureHandler implements ITopologyListener, IPESubmodule {

	private static final Logger log = LoggerFactory.getLogger(LinkFailureHandler.class);
	private ITopologyService topologyService;

	@Override
	public void startUp(FloodlightModuleContext context) {
		topologyService = context.getServiceImpl(ITopologyService.class);
		topologyService.addListener(this);
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		return Collections.singletonList(ITopologyService.class);
	}

	@Override
	public void topologyChanged(List<ILinkDiscovery.LDUpdate> linkUpdates) {
		List<ILinkDiscovery.LDUpdate> updateRemoved = linkUpdates.stream()
				.filter(u -> u.getOperation() == ILinkDiscovery.UpdateOperation.LINK_REMOVED)
				.collect(Collectors.toList());
		if (!updateRemoved.isEmpty()) {
            log.info("Link removed! {}", updateRemoved);
        }
	}
}
