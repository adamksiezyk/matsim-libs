/* *********************************************************************** *
 * project: org.matsim.*
 * TransitRouterNetworkWW.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.contrib.eventsBasedPTRouter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.*;

/**
 * Transit router network with travel, transfer, and waiting links
 * 
 * @author sergioo
 */

public final class TransitRouterNetworkWW implements Network {

	private final static Logger log = LogManager.getLogger(TransitRouterNetworkWW.class);
	
	private final Map<Id<Link>, TransitRouterNetworkLink> links = new LinkedHashMap<>();
	private final Map<Id<Node>, TransitRouterNetworkNode> nodes = new LinkedHashMap<>();
	protected QuadTree<TransitRouterNetworkNode> qtNodes = null;

	private long nextNodeId = 0;
	protected long nextLinkId = 0;

	public static final class TransitRouterNetworkNode implements Node {

		public final TransitRouteStop stop;
		public final TransitRoute route;
		public final TransitLine line;
		final Id<Node> id;
		final Map<Id<Link>, TransitRouterNetworkLink> ingoingLinks = new LinkedHashMap<>();
		final Map<Id<Link>, TransitRouterNetworkLink> outgoingLinks = new LinkedHashMap<>();

		public TransitRouterNetworkNode(final Id<Node> id, final TransitRouteStop stop, final TransitRoute route, final TransitLine line) {
			this.id = id;
			this.stop = stop;
			this.route = route;
			this.line = line;
		}

		@Override
		public Map<Id<Link>, ? extends Link> getInLinks() {
			return this.ingoingLinks;
		}

		@Override
		public Map<Id<Link>, ? extends Link> getOutLinks() {
			return this.outgoingLinks;
		}

		@Override
		public boolean addInLink(final Link link) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean addOutLink(final Link link) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Coord getCoord() {
			return this.stop.getStopFacility().getCoord();
		}

		@Override
		public Id<Node> getId() {
			return this.id;
		}

		public TransitRouteStop getStop() {
			return stop;
		}

		public TransitRoute getRoute() {
			return route;
		}

		public TransitLine getLine() {
			return line;
		}

		@Override
		public Link removeInLink(Id<Link> linkId) {
			throw new RuntimeException("not implemented") ;
		}

		@Override
		public Link removeOutLink(Id<Link> outLinkId) {
			throw new RuntimeException("not implemented") ;
		}

		@Override
		public void setCoord(Coord coord) {
			throw new RuntimeException("not implemented") ;
		}

		@Override
		public Attributes getAttributes() {
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * Looks to me like an implementation of the Link interface, with get(Transit)Route and get(Transit)Line on top.
	 * To recall: TransitLine is something like M44.  But it can have more than one route, e.g. going north, going south,
	 * long route, short route. That is, presumably we have one such TransitRouterNetworkLink per TransitRoute. kai/manuel, feb'12
	 */
	public static final class TransitRouterNetworkLink implements Link {

		final TransitRouterNetworkNode fromNode;
		final TransitRouterNetworkNode toNode;
		final TransitRoute route;
		final TransitLine line;
		final Id<Link> id;
		private double length;

		public TransitRouterNetworkLink(final Id<Link> id, final TransitRouterNetworkNode fromNode, final TransitRouterNetworkNode toNode, final TransitRoute route, final TransitLine line, Network network) {
			this.id = id;
			this.fromNode = fromNode;
			this.toNode = toNode;
			this.route = route;
			this.line = line;
			if(route==null)
				this.length = CoordUtils.calcEuclideanDistance(this.toNode.stop.getStopFacility().getCoord(), this.fromNode.stop.getStopFacility().getCoord());
			else {
				this.length = 0;
				for(Id<Link> linkId:route.getRoute().getSubRoute(fromNode.stop.getStopFacility().getLinkId(), toNode.stop.getStopFacility().getLinkId()).getLinkIds())
					this.length += network.getLinks().get(linkId).getLength();
				this.length += network.getLinks().get(toNode.stop.getStopFacility().getLinkId()).getLength();
			}
		}

		@Override
		public TransitRouterNetworkNode getFromNode() {
			return this.fromNode;
		}

		@Override
		public TransitRouterNetworkNode getToNode() {
			return this.toNode;
		}

		@Override
		public double getCapacity() {
			return 9999;
		}

		@Override
		public double getCapacity(final double time) {
			return getCapacity();
		}

		@Override
		public double getFreespeed() {
			return 10;
		}

		@Override
		public double getFreespeed(final double time) {
			return getFreespeed();
		}

		@Override
		public Id<Link> getId() {
			return this.id;
		}

		@Override
		public double getNumberOfLanes() {
			return 1;
		}

		@Override
		public double getNumberOfLanes(final double time) {
			return getNumberOfLanes();
		}

		@Override
		public double getLength() {
			return this.length;
		}

		@Override
		public void setCapacity(final double capacity) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setFreespeed(final double freespeed) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean setFromNode(final Node node) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setNumberOfLanes(final double lanes) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setLength(final double length) {
			this.length = length;
		}

		@Override
		public boolean setToNode(final Node node) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Coord getCoord() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Set<String> getAllowedModes() {
			return null;
		}

		@Override
		public void setAllowedModes(final Set<String> modes) {
			throw new UnsupportedOperationException();
		}

		public TransitRoute getRoute() {
			return route;
		}

		public TransitLine getLine() {
			return line;
		}

		@Override
		public double getCapacityPeriod() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Attributes getAttributes() {
			throw new UnsupportedOperationException();
		}
	}
	public TransitRouterNetworkNode createNode(final TransitRouteStop stop, final TransitRoute route, final TransitLine line) {
		Id<Node> id = null;
		if(line==null && route==null)
			id = Id.createNodeId(stop.getStopFacility().getId().toString());
		else
			id = Id.createNodeId("number:"+nextNodeId++);
		final TransitRouterNetworkNode node = new TransitRouterNetworkNode(id, stop, route, line);
		if(this.nodes.get(node.getId())!=null)
			throw new RuntimeException();
		this.nodes.put(node.getId(), node);
		return node;
	}

	public TransitRouterNetworkLink createLink(final Network network, final TransitRouterNetworkNode fromNode, final TransitRouterNetworkNode toNode) {
		final TransitRouterNetworkLink link = new TransitRouterNetworkLink(Id.createLinkId(this.nextLinkId++), fromNode, toNode, null, null, network);
		this.links.put(link.getId(), link);
		fromNode.outgoingLinks.put(link.getId(), link);
		toNode.ingoingLinks.put(link.getId(), link);
		return link;
	}
	public TransitRouterNetworkLink createLink(final Network network, final TransitRouterNetworkNode fromNode, final TransitRouterNetworkNode toNode, final TransitRoute route, final TransitLine line) {
		final TransitRouterNetworkLink link = new TransitRouterNetworkLink(Id.createLinkId(this.nextLinkId++), fromNode, toNode, route, line, network);
		this.getLinks().put(link.getId(), link);
		fromNode.outgoingLinks.put(link.getId(), link);
		toNode.ingoingLinks.put(link.getId(), link);
		return link;
	}
	@Override
	public Map<Id<Node>, TransitRouterNetworkNode> getNodes() {
		return this.nodes;
	}
	@Override
	public Map<Id<Link>, TransitRouterNetworkLink> getLinks() {
		return this.links;
	}
	public void finishInit() {
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		for (TransitRouterNetworkNode node : getNodes().values())
			if(node.line == null) {
				Coord c = node.stop.getStopFacility().getCoord();
				if (c.getX() < minX)
					minX = c.getX();
				if (c.getY() < minY)
					minY = c.getY();
				if (c.getX() > maxX)
					maxX = c.getX();
				if (c.getY() > maxY)
					maxY = c.getY();
			}
		QuadTree<TransitRouterNetworkNode> quadTree = new QuadTree<TransitRouterNetworkNode>(minX, minY, maxX, maxY);
		for (TransitRouterNetworkNode node : getNodes().values()) {
			if(node.line == null) {	
				Coord c = node.stop.getStopFacility().getCoord();
				quadTree.put(c.getX(), c.getY(), node);
			}
		}
		this.qtNodes = quadTree;
	}
	public static TransitRouterNetworkWW createFromSchedule(final Network network, final TransitSchedule schedule, final double maxBeelineWalkConnectionDistance) {
		log.info("start creating transit network");
		final TransitRouterNetworkWW transitNetwork = new TransitRouterNetworkWW();
		final Counter linkCounter = new Counter(" link #");
		final Counter nodeCounter = new Counter(" node #");
		int numTravelLinks = 0, numWaitingLinks = 0, numInsideLinks = 0, numTransferLinks = 0;
		Map<Id<TransitStopFacility>, TransitRouterNetworkNode> stops = new HashMap<Id<TransitStopFacility>, TransitRouterNetworkNode>();
		TransitRouterNetworkNode nodeSR, nodeS;
		// build stop nodes
		for (TransitLine line : schedule.getTransitLines().values())
			for (TransitRoute route : line.getRoutes().values())
				for (TransitRouteStop stop : route.getStops()) {
					nodeS = stops.get(stop.getStopFacility().getId());
					if(nodeS == null) {
						nodeS = transitNetwork.createNode(stop, null, null);
						nodeCounter.incCounter();
						stops.put(stop.getStopFacility().getId(), nodeS);
					}
				}
		transitNetwork.finishInit();
		// build transfer links
		log.info("add transfer links");
		// connect all stops with walking links if they're located less than beelineWalkConnectionDistance from each other
		for (TransitRouterNetworkNode node : transitNetwork.getNodes().values())
			for (TransitRouterNetworkNode node2 : transitNetwork.getNearestNodes(node.stop.getStopFacility().getCoord(), maxBeelineWalkConnectionDistance))
				if (node!=node2) {
					transitNetwork.createLink(network, node, node2);
					linkCounter.incCounter();
					numTransferLinks++;
				}
		// build nodes and links connecting the nodes according to the transit routes
		log.info("add travel, waiting and inside links");
		for (TransitLine line : schedule.getTransitLines().values())
			for (TransitRoute route : line.getRoutes().values()) {
				TransitRouterNetworkNode prevNode = null;
				for (TransitRouteStop stop : route.getStops()) {
					nodeS = stops.get(stop.getStopFacility().getId());
					nodeSR = transitNetwork.createNode(stop, route, line);
					nodeCounter.incCounter();
					if (prevNode != null) {
						transitNetwork.createLink(network, prevNode, nodeSR, route, line);
						linkCounter.incCounter();
						numTravelLinks++;
					}
					prevNode = nodeSR;
					transitNetwork.createLink(network, nodeS, nodeSR);
					linkCounter.incCounter();
					numWaitingLinks++;
					transitNetwork.createLink(network, nodeSR, nodeS);
					linkCounter.incCounter();
					numInsideLinks++;
				}
			}
		log.info("transit router network statistics:");
		log.info(" # nodes: " + transitNetwork.getNodes().size());
		log.info(" # links total:     " + transitNetwork.getLinks().size());
		log.info(" # travel links:  " + numTravelLinks);
		log.info(" # waiting links:  " + numWaitingLinks);
		log.info(" # inside links:  " + numInsideLinks);
		log.info(" # transfer links:  " + numTransferLinks);
		return transitNetwork;
	}
	public Collection<TransitRouterNetworkNode> getNearestNodes(final Coord coord, final double distance) {
		return this.qtNodes.getDisk(coord.getX(), coord.getY(), distance);
	}

	public TransitRouterNetworkNode getNearestNode(final Coord coord) {
		return this.qtNodes.getClosest(coord.getX(), coord.getY());
	}

	@Override
	public double getCapacityPeriod() {
		return 3600.0;
	}

	@Override
	public NetworkFactory getFactory() {
		return null;
	}

	@Override
	public double getEffectiveLaneWidth() {
		return 3;
	}

	@Override
	public void addNode(Node nn) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addLink(Link ll) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Link removeLink(Id<Link> linkId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Node removeNode(Id<Node> nodeId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setCapacityPeriod(double capPeriod) {
		throw new RuntimeException("not implemented") ;
	}

	@Override
	public void setEffectiveCellSize(double effectiveCellSize) {
		throw new RuntimeException("not implemented") ;
	}

	@Override
	public void setEffectiveLaneWidth(double effectiveLaneWidth) {
		throw new RuntimeException("not implemented") ;
	}

	@Override
	public void setName(String name) {
		throw new RuntimeException("not implemented") ;
	}

	@Override
	public String getName() {
		throw new RuntimeException("not implemented") ;
	}

	@Override
	public double getEffectiveCellSize() {
		throw new RuntimeException("not implemented") ;
	}

	@Override
	public Attributes getAttributes() {
		throw new UnsupportedOperationException();
	}
}
