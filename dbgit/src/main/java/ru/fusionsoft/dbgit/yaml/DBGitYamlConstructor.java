package ru.fusionsoft.dbgit.yaml;

import java.util.HashMap;
import java.util.Map;

import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Construct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;

import ru.fusionsoft.dbgit.utils.StringProperties;

public class DBGitYamlConstructor extends Constructor {
	protected final Map<Class<?>, Construct> constructorsByClass = new HashMap<Class<?>, Construct>();
	
	public DBGitYamlConstructor() {
		constructorsByClass.put(StringProperties.class, new ConstructYamlStringProperties());
	}
	
	@Override
    protected Construct getConstructor(Node node) {
    	if (constructorsByClass.containsKey(node.getType())) {
    		return constructorsByClass.get(node.getType());
    	}
    	
    	return super.getConstructor(node);
    }
    
    /*
    @Override
    protected Object constructObject(Node node)
    */
	
	
    private class ConstructYamlStringProperties extends AbstractConstruct { 
        @Override
        public Object construct(Node node) {        	
        	Object obj = constructMapping((MappingNode)node);
    		
    		StringProperties properties = new StringProperties();
    		parseMap(properties, obj);
        	
        	return properties;
        }
        
        @SuppressWarnings("unchecked")
        public void parseMap(StringProperties pr, Object obj) {
        	if (obj instanceof Map) {
        		Map<String, String> map =  (Map<String, String>)obj;
        		for( String key : map.keySet()) {        			
        			StringProperties newPr = (StringProperties)pr.addChild(key);
        			parseMap(newPr, map.get(key));
        		}
        	} else {        		
        		pr.setData((String)obj);
        	}
        }
    }
}