package top.jionjion.agentdesk.agent.tool;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiCallToolTest {

    private final ApiCallTool tool = new ApiCallTool();

    @Test
    void blocksLocalAndPrivateAddresses() throws Exception {
        assertTrue(tool.isBlockedAddress(InetAddress.getByName("0.0.0.0")));
        assertTrue(tool.isBlockedAddress(InetAddress.getByName("127.0.0.1")));
        assertTrue(tool.isBlockedAddress(InetAddress.getByName("10.0.0.1")));
        assertTrue(tool.isBlockedAddress(InetAddress.getByName("172.16.0.1")));
        assertTrue(tool.isBlockedAddress(InetAddress.getByName("192.168.1.1")));
        assertTrue(tool.isBlockedAddress(InetAddress.getByName("169.254.1.1")));
        assertTrue(tool.isBlockedAddress(InetAddress.getByName("100.64.0.1")));
        assertTrue(tool.isBlockedAddress(InetAddress.getByName("fc00::1")));
    }

    @Test
    void allowsPublicAddresses() throws Exception {
        assertFalse(tool.isBlockedAddress(InetAddress.getByName("8.8.8.8")));
        assertFalse(tool.isBlockedAddress(InetAddress.getByName("100.128.0.1")));
        assertFalse(tool.isBlockedAddress(InetAddress.getByName("2001:4860:4860::8888")));
    }
}
