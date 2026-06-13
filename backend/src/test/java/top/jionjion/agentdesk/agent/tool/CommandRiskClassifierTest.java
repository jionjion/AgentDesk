package top.jionjion.agentdesk.agent.tool;

import org.junit.jupiter.api.Test;
import top.jionjion.agentdesk.websocket.dto.CommandRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandRiskClassifierTest {

    private final CommandRiskClassifier classifier = new CommandRiskClassifier(
            "ls,cat,head,tail,find,grep,wc,pwd,echo,date,whoami,which,type,file"
    );

    @Test
    void classifiesSingleAmpersandAsHighRisk() {
        assertEquals(CommandRequest.RISK_HIGH, classifier.classify("echo ok & whoami"));
    }

    @Test
    void classifiesSafeChainedCommandsAsLowRisk() {
        assertEquals(CommandRequest.RISK_LOW, classifier.classify("echo ok && pwd"));
    }

    @Test
    void classifiesEnvironmentDumpAsHighRisk() {
        assertEquals(CommandRequest.RISK_HIGH, classifier.classify("env"));
        assertEquals(CommandRequest.RISK_HIGH, classifier.classify("printenv"));
    }

    @Test
    void classifiesDangerousFindArgsAsHighRisk() {
        assertEquals(CommandRequest.RISK_HIGH, classifier.classify("find . -delete"));
        assertEquals(CommandRequest.RISK_HIGH, classifier.classify("find . -exec rm {} ;"));
    }
}
