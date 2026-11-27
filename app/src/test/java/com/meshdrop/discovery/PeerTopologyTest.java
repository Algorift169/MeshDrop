package com.meshdrop.discovery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.meshdrop.data.model.PeerTreeNode;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class PeerTopologyTest {
    @Test
    public void buildsTreeForPeersBeyondDirectRange() {
        PeerTopology topology = new PeerTopology("node-a", "User One");
        PeerTopology.Route userThree = new PeerTopology.Route(
                "node-c", "User Three",
                List.of("node-b", "node-c"),
                List.of("User Two", "User Three"), null);

        topology.updateFromNeighbor("endpoint-b", "node-b", "User Two",
                Collections.singletonList(userThree));

        List<PeerTreeNode> peers = topology.getTreeNodes();
        assertEquals(2, peers.size());
        PeerTreeNode userTwo = findPeer(peers, "node-b");
        PeerTreeNode userThreeNode = findPeer(peers, "node-c");
        assertEquals("node-a", userTwo.getParentId());
        assertEquals("node-b", userThreeNode.getParentId());
        assertEquals(2, userThreeNode.getHopCount());
    }

    @Test
    public void removesRoutesWhenTheirDirectRelayDisconnects() {
        PeerTopology topology = new PeerTopology("node-a", "User One");
        topology.updateFromNeighbor("endpoint-b", "node-b", "User Two",
                Collections.emptyList());

        topology.removeNeighbor("endpoint-b");

        assertEquals(0, topology.getTreeNodes().size());
    }

    private PeerTreeNode findPeer(List<PeerTreeNode> peers, String peerId) {
        for (PeerTreeNode peer : peers) {
            if (peerId.equals(peer.getPeerId())) return peer;
        }
        throw new AssertionError("Missing peer " + peerId);
    }
}
