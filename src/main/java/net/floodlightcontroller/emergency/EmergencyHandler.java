package net.floodlightcontroller.emergency;

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
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Path;
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
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class EmergencyHandler implements IOFMessageListener, IFloodlightModule {

	private static final Logger log = LoggerFactory.getLogger(EmergencyHandler.class);

	private IFloodlightProviderService floodlightProvider;
	private IRoutingService routingService;
	private IDeviceService deviceManagerService;

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
		IPv4 ipPacket = (IPv4) ethernet.getPayload();
		IPv4Address prioSrc = IPv4Address.of("10.0.0.1");
		IPv4Address prioDest = IPv4Address.of("10.0.0.4");
		boolean isPriority = (ipPacket.getSourceAddress().equals(prioSrc) && ipPacket.getDestinationAddress().equals(prioDest))
				|| (ipPacket.getDestinationAddress().equals(prioSrc) && ipPacket.getSourceAddress().equals(prioDest));
		if (!((ipPacket.getPayload()) instanceof TCP)) {
			return Command.CONTINUE;
		}
		if (!isPriority) {
			log.debug("Detected non-priority flow TCP");
			IDevice dstDevice = getDevice(cntx, ipPacket);
			if (dstDevice == null) {
				log.error("Couldn't determine destination device with IP {}", ipPacket.getDestinationAddress());
				return Command.CONTINUE;
			}
			Path path = routingService.getPath(sw.getId(), dstDevice.getAttachmentPoints()[0].getNodeId());
			if (path.getHopCount() <= 0) {
				log.debug("No flow entry needed because hops <= 0");
				return Command.CONTINUE;
			}
			OFPort port = path.getPath().get(0).getPortId();

			addNonPriorityFlowEntry(sw, ipPacket.getSourceAddress(), ipPacket.getDestinationAddress(), port);
		} else {
			log.debug("Detected priority traffic");
		}
		return Command.CONTINUE;
	}

	private void addNonPriorityFlowEntry(IOFSwitch sw, IPv4Address source, IPv4Address dest, OFPort port) {
		OFFactory factory = sw.getOFFactory();
		OFFlowAdd.Builder flowAddBuilder = factory.buildFlowAdd();
		Match match = factory.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
				.setExact(MatchField.IPV4_SRC, source)
				.setExact(MatchField.IPV4_DST, dest)
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
				.setPort(port)
				.build();
		actions.add(outputAction);

		flowAddBuilder.setActions(actions);
		flowAddBuilder.setPriority(10);
		flowAddBuilder.setIdleTimeout(0);
		OFFlowAdd flowAdd = flowAddBuilder.build();
		sw.write(flowAdd);
		log.info("Added flow to switch=" + sw + ", flow=" + flowAdd);
	}

	private IDevice getDevice(FloodlightContext cntx, IPv4 ipPacket) {
		IDevice dstDevice = IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
		if (dstDevice == null) {
			dstDevice = findDeviceByIP(ipPacket.getDestinationAddress());
		}
		return dstDevice;
	}

	private IDevice findDeviceByIP(IPv4Address ip) {
		// Fetch all known devices
		Collection<? extends IDevice> allDevices = deviceManagerService.getAllDevices();

		IDevice dstDevice = null;
		for (IDevice d : allDevices) {
			for (int i = 0; i < d.getIPv4Addresses().length; ++i) {
				if (d.getIPv4Addresses()[i].equals(ip)) {
					dstDevice = d;
					break;
				}
			}
		}
		return dstDevice;
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
		routingService = context.getServiceImpl(IRoutingService.class);
		deviceManagerService = context.getServiceImpl(IDeviceService.class);
		log.info("Initialized " + getName());
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		log.info("Startup " + getName());
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}
}
