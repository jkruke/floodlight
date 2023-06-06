package net.floodlightcontroller.emergency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetQueue;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Path;

public class EmergencyHandler implements IOFMessageListener, IFloodlightModule {

	private static Logger log = LoggerFactory.getLogger(EmergencyHandler.class);

	private IFloodlightProviderService floodlightProvider;
	private IRoutingService routingEngineService;

	@Override
	public String getName() {
		return EmergencyHandler.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		Ethernet ethernet = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		if (!(ethernet.getPayload() instanceof IPv4)) {
			return Command.CONTINUE;
		}
		IPacket ipPayload = (ethernet.getPayload()).getPayload();
		List<Match> matches = new ArrayList<>();
		List<OFAction> actions = new ArrayList<>();

		if (ipPayload instanceof UDP) {
			log.info("Detected priority flow UDP");
			Optional<OFAction> action = getPriorityFlowAction(sw, cntx);
			if (action.isPresent()) {
				actions.add(action.get());
				Match match = sw.getOFFactory()
						.buildMatch()
						.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
						.build();
				matches.add(match);
			}
		} else if (ipPayload instanceof TCP) {
			log.info("Detected non-priority flow TCP");
			OFActionSetQueue setQueueAction = sw.getOFFactory()
					.actions()
					.buildSetQueue()
					.setQueueId(0)
					.build();
			actions.add(setQueueAction);
			Match match = sw.getOFFactory()
					.buildMatch()
					.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
					.build();
			matches.add(match);
		}

		if (!actions.isEmpty()) {
			OFFlowAdd.Builder flowAddBuilder = sw.getOFFactory()
					.buildFlowAdd()
					.setActions(actions);
			matches.forEach(flowAddBuilder::setMatch);
			flowAddBuilder.setPriority(10);
			OFFlowAdd flowAdd = flowAddBuilder.build();
			sw.write(flowAdd);
			log.info("Added flow to switch=" + sw + ", flow=" + flowAdd);
		}
		return Command.CONTINUE;
	}

	private Optional<OFAction> getPriorityFlowAction(IOFSwitch sw, FloodlightContext cntx) {
		IDevice dstDevice = IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
		Path path = routingEngineService.getPath(sw.getId(), dstDevice.getAttachmentPoints()[0].getNodeId());
		if (path.getHopCount() == 0) {
			return Optional.empty();
		}
		OFPort outPort = path.getPath()
				.get(0)
				.getPortId();
		OFActionOutput outputAction = sw.getOFFactory()
				.actions()
				.buildOutput()
				.setPort(outPort)
				.build();
		return Optional.of(outputAction);
	}

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
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		routingEngineService = context.getServiceImpl(IRoutingService.class);
		log.info("Initialized " + getName());
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		log.info("Startup " + getName());
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}
}
