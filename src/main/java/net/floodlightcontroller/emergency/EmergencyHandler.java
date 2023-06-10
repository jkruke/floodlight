package net.floodlightcontroller.emergency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetQueue;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
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
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.routing.IRoutingService;

public class EmergencyHandler implements IOFMessageListener, IFloodlightModule {

	private static final Logger log = LoggerFactory.getLogger(EmergencyHandler.class);

	private IFloodlightProviderService floodlightProvider;

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
		if (!(ipPayload instanceof TCP)) {
			return Command.CONTINUE;
		}

		OFFactory factory = sw.getOFFactory();
		OFFlowAdd.Builder flowAddBuilder = factory.buildFlowAdd();
		log.debug("Detected non-priority flow TCP");
		Match match = factory.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
				.build();
		flowAddBuilder.setMatch(match);

		List<OFAction> actions = new ArrayList<>();
		OFActionSetQueue setQueueAction = factory.actions()
				.buildSetQueue()
				.setQueueId(0)
				.build();
		actions.add(setQueueAction);

		OFActionOutput outputAction = factory.actions()
				.buildOutput()
				.setPort(OFPort.NORMAL)
				.build();
		actions.add(outputAction);

		flowAddBuilder.setActions(actions);
		flowAddBuilder.setPriority(10);
		flowAddBuilder.setIdleTimeout(0);
		OFFlowAdd flowAdd = flowAddBuilder.build();
		sw.write(flowAdd);
		log.info("Added flow to switch=" + sw + ", flow=" + flowAdd);
		return Command.STOP;
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
		return Arrays.asList(IFloodlightProviderService.class, IRoutingService.class);
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		log.info("Initialized " + getName());
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		log.info("Startup " + getName());
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}
}
