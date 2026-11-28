package com.meshdrop.discovery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.meshdrop.data.model.PeerTreeNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PeerTopology {
    static final int MAX_HOPS = 7;
    private static final int MAX_ADVERTISED_ROUTES = 32;

    private final String localPeerId;
    private String localName;
    private final Map<String, Map<String, Route>> routesByEndpoint = new HashMap<>();
    private Map<String, Route> bestRoutes = new HashMap<>();

    PeerTopology(@NonNull String localPeerId, @NonNull String localName) {
        this.localPeerId = localPeerId;
        this.localName = localName;
    }

    synchronized void setLocalName(@NonNull String name) {
        localName = name;
    }

    synchronized boolean updateFromNeighbor(
            @NonNull String endpointId,
            @NonNull String neighborId,
            @NonNull String neighborName,
            @NonNull List<Route> advertisedRoutes) {
        Map<String, Route> snapshot = new HashMap<>();
        List<String> directIds = new ArrayList<>();
        directIds.add(localPeerId);
        directIds.add(neighborId);
        List<String> directNames = new ArrayList<>();
        directNames.add(localName);
        directNames.add(neighborName);
        snapshot.put(neighborId, new Route(neighborId, neighborName,
                directIds, directNames, endpointId));

        for (Route advertised : advertisedRoutes) {
            List<String> ids = advertised.getPathIds();
            List<String> names = advertised.getPathNames();
            if (ids.isEmpty() || !neighborId.equals(ids.get(0))
                    || ids.size() >= MAX_HOPS + 1
                    || ids.size() != names.size()
                    || ids.contains(localPeerId)
                    || new HashSet<>(ids).size() != ids.size()) {
                continue;
            }

            List<String> pathIds = new ArrayList<>(ids.size() + 1);
            List<String> pathNames = new ArrayList<>(names.size() + 1);
            pathIds.add(localPeerId);
            pathIds.addAll(ids);
            pathNames.add(localName);
            pathNames.addAll(names);
            String targetId = pathIds.get(pathIds.size() - 1);
            if (targetId.equals(neighborId)) continue;
            snapshot.put(targetId, new Route(targetId,
                    pathNames.get(pathNames.size() - 1), pathIds, pathNames, null));
        }

        routesByEndpoint.put(endpointId, snapshot);
        return recompute();
    }

    synchronized boolean updateFromNeighborTree(
            @NonNull String endpointId,
            @NonNull String neighborId,
            @NonNull String neighborName,
            @NonNull List<PeerTreeNode> advertisedPeers) {
        Map<String, PeerTreeNode> peersById = new HashMap<>();
        for (PeerTreeNode peer : advertisedPeers) {
            if (!peer.getPeerId().equals(localPeerId)
                    && !peer.getPeerId().equals(neighborId)) {
                peersById.put(peer.getPeerId(), peer);
            }
        }

        List<Route> routes = new ArrayList<>();
        for (PeerTreeNode target : peersById.values()) {
            List<String> reversedIds = new ArrayList<>();
            List<String> reversedNames = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            PeerTreeNode current = target;
            boolean reachedNeighbor = false;
            while (current != null && reversedIds.size() < MAX_HOPS
                    && visited.add(current.getPeerId())) {
                reversedIds.add(current.getPeerId());
                reversedNames.add(current.getDeviceName());
                String parentId = current.getParentId();
                if (neighborId.equals(parentId)) {
                    reachedNeighbor = true;
                    break;
                }
                current = parentId == null ? null : peersById.get(parentId);
            }
            if (!reachedNeighbor || reversedIds.size() > MAX_HOPS) continue;

            Collections.reverse(reversedIds);
            Collections.reverse(reversedNames);
            List<String> pathIds = new ArrayList<>(reversedIds.size() + 1);
            List<String> pathNames = new ArrayList<>(reversedNames.size() + 1);
            pathIds.add(neighborId);
            pathIds.addAll(reversedIds);
            pathNames.add(neighborName);
            pathNames.addAll(reversedNames);
            routes.add(new Route(target.getPeerId(), target.getDeviceName(),
                    pathIds, pathNames, null));
        }
        return updateFromNeighbor(endpointId, neighborId, neighborName, routes);
    }

    synchronized boolean removeNeighbor(@NonNull String endpointId) {
        routesByEndpoint.remove(endpointId);
        return recompute();
    }

    synchronized boolean clear() {
        routesByEndpoint.clear();
        return recompute();
    }

    @NonNull
    synchronized List<Route> getAdvertisedRoutes() {
        List<Route> routes = new ArrayList<>(bestRoutes.values());
        Collections.sort(routes, (left, right) -> {
            int hopComparison = Integer.compare(left.getPathIds().size(), right.getPathIds().size());
            return hopComparison != 0
                    ? hopComparison
                    : left.getPeerId().compareTo(right.getPeerId());
        });
        if (routes.size() > MAX_ADVERTISED_ROUTES) {
            routes = new ArrayList<>(routes.subList(0, MAX_ADVERTISED_ROUTES));
        }
        return routes;
    }

    @NonNull
    synchronized List<PeerTreeNode> getTreeNodes() {
        List<PeerTreeNode> nodes = new ArrayList<>();
        for (Route route : getAdvertisedRoutes()) {
            List<String> ids = route.getPathIds();
            if (ids.size() < 2) continue;
            String parentId = ids.get(ids.size() - 2);
            nodes.add(new PeerTreeNode(route.getPeerId(), route.getDeviceName(),
                    parentId, route.getEndpointId(), ids.size() - 1));
        }
        return nodes;
    }

    @NonNull
    synchronized String getLocalPeerId() {
        return localPeerId;
    }

    @NonNull
    synchronized String getLocalName() {
        return localName;
    }

    private boolean recompute() {
        Map<String, Route> next = new HashMap<>();
        for (Map<String, Route> snapshot : routesByEndpoint.values()) {
            for (Map.Entry<String, Route> entry : snapshot.entrySet()) {
                Route candidate = entry.getValue();
                Route existing = next.get(entry.getKey());
                if (existing == null || candidate.isPreferredTo(existing)) {
                    next.put(entry.getKey(), candidate);
                }
            }
        }
        boolean changed = !sameRoutes(bestRoutes, next);
        bestRoutes = next;
        return changed;
    }

    private boolean sameRoutes(Map<String, Route> first, Map<String, Route> second) {
        if (!first.keySet().equals(second.keySet())) return false;
        for (String id : first.keySet()) {
            Route left = first.get(id);
            Route right = second.get(id);
            if (left == null || right == null || !left.samePath(right)) return false;
        }
        return true;
    }

    static final class Route {
        private final String peerId;
        private final String deviceName;
        private final List<String> pathIds;
        private final List<String> pathNames;
        @Nullable
        private final String endpointId;

        Route(@NonNull String peerId, @NonNull String deviceName,
              @NonNull List<String> pathIds, @NonNull List<String> pathNames,
              @Nullable String endpointId) {
            this.peerId = peerId;
            this.deviceName = deviceName;
            this.pathIds = Collections.unmodifiableList(new ArrayList<>(pathIds));
            this.pathNames = Collections.unmodifiableList(new ArrayList<>(pathNames));
            this.endpointId = endpointId;
        }

        @NonNull
        String getPeerId() {
            return peerId;
        }

        @NonNull
        String getDeviceName() {
            return deviceName;
        }

        @NonNull
        List<String> getPathIds() {
            return pathIds;
        }

        @NonNull
        List<String> getPathNames() {
            return pathNames;
        }

        @Nullable
        String getEndpointId() {
            return pathIds.size() == 2 ? endpointId : null;
        }

        private boolean isPreferredTo(@NonNull Route other) {
            if (pathIds.size() != other.pathIds.size()) {
                return pathIds.size() < other.pathIds.size();
            }
            return pathIds.toString().compareTo(other.pathIds.toString()) < 0;
        }

        private boolean samePath(@NonNull Route other) {
            return pathIds.equals(other.pathIds)
                    && pathNames.equals(other.pathNames)
                    && equals(endpointId, other.endpointId);
        }

        private boolean equals(@Nullable Object first, @Nullable Object second) {
            return first == second || (first != null && first.equals(second));
        }
    }
}
