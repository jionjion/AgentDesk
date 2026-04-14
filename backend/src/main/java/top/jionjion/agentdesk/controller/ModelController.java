package top.jionjion.agentdesk.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.jionjion.agentdesk.dto.ModelDefinition;
import top.jionjion.agentdesk.service.ModelRegistryService;

import java.util.List;
import java.util.Map;

/**
 * 模型列表控制器
 *
 * @author Jion
 */
@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final ModelRegistryService modelRegistryService;

    public ModelController(ModelRegistryService modelRegistryService) {
        this.modelRegistryService = modelRegistryService;
    }

    /** 获取所有可用模型 */
    @GetMapping
    public List<ModelDefinition> listModels() {
        return modelRegistryService.listModels();
    }

    /** 按分组获取模型列表 */
    @GetMapping("/grouped")
    public Map<String, List<ModelDefinition>> listGrouped() {
        return modelRegistryService.listGrouped();
    }
}
