package i5.las2peer.services.socialBotManagerService.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import i5.las2peer.api.Context;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.api.security.AgentException;
import i5.las2peer.api.security.AgentNotFoundException;
import i5.las2peer.security.BotAgent;
import i5.las2peer.services.socialBotManagerService.chat.Slack;
import i5.las2peer.services.socialBotManagerService.model.ActionType;
import i5.las2peer.services.socialBotManagerService.model.Bot;
import i5.las2peer.services.socialBotManagerService.model.BotConfiguration;
import i5.las2peer.services.socialBotManagerService.model.BotModelEdge;
import i5.las2peer.services.socialBotManagerService.model.BotModelNode;
import i5.las2peer.services.socialBotManagerService.model.BotModelNodeAttribute;
import i5.las2peer.services.socialBotManagerService.model.BotModelValue;
import i5.las2peer.services.socialBotManagerService.model.ContentGenerator;
import i5.las2peer.services.socialBotManagerService.model.IfThenBlock;
import i5.las2peer.services.socialBotManagerService.model.ServiceFunction;
import i5.las2peer.services.socialBotManagerService.model.ServiceFunctionAttribute;
import i5.las2peer.services.socialBotManagerService.model.Trigger;
import i5.las2peer.services.socialBotManagerService.model.VLE;
import i5.las2peer.services.socialBotManagerService.model.VLERoutine;
import i5.las2peer.services.socialBotManagerService.model.VLEUser;
import i5.las2peer.tools.CryptoException;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

public class BotParser {
	private static BotParser instance = null;
	private static final String botPass = "actingAgent";
	private static final String classifierName = "i5.las2peer.services.tensorFlowClassifier.TensorFlowClassifier";
	private static final String textToTextName = "i5.las2peer.services.tensorFlowTextToText.TensorFlowTextToText";

	protected BotParser() {
	}

	public static BotParser getInstance() {
		if (instance == null) {
			instance = new BotParser();
		}
		return instance;
	}

	public void parseNodesAndEdges(BotConfiguration config, HashMap<String, BotAgent> botAgents, LinkedHashMap<String, BotModelNode> nodes,
			LinkedHashMap<String, BotModelEdge> edges) throws ParseBotException {
		
		HashMap<String, VLE> vles = new HashMap<String, VLE>();
		HashMap<String, VLEUser> users = new HashMap<String, VLEUser>();
		HashMap<String, Bot> bots = new HashMap<String, Bot>();

		HashMap<String, ServiceFunction> bsfList = new HashMap<String, ServiceFunction>();
		HashMap<String, ServiceFunction> usfList = new HashMap<String, ServiceFunction>();
		HashMap<String, ServiceFunctionAttribute> sfaList = new HashMap<String, ServiceFunctionAttribute>();

		HashMap<String, ContentGenerator> gList = new HashMap<String, ContentGenerator>();

		HashMap<String, IfThenBlock> itbList = new HashMap<String, IfThenBlock>();
		HashMap<String, VLERoutine> rlist = new HashMap<String, VLERoutine>();


		int vleCount = 0;
		VLE vle = null;
		
		// NODES
		for (Entry<String, BotModelNode> entry : nodes.entrySet()) {
			BotModelNode elem = entry.getValue();
			String nodeType = elem.getType();
			// VLE 
			if (nodeType.equals("Instance")) {
				vle = setVLEInstance(elem);
				config.addServiceConfiguration(vle.getName(), vle);
				vles.put(entry.getKey(), vle);
				vleCount++;
			// VLE User
			} else if (nodeType.equals("User")) {
				VLEUser u = addUser(elem);
				u.setId(entry.getKey());
				users.put(entry.getKey(), u);
			// Bot
			} else if (nodeType.equals("Bot")) {
				Bot bot = addBot(elem, botAgents);
				bots.put(entry.getKey(), bot);
			// VLE Routine
			} else if (nodeType.equals("Routine")) {
				VLERoutine routine = addRoutine(elem);
				rlist.put(entry.getKey(), routine);
			// BOT Action
			} else if (nodeType.equals("IfThen")) {
				IfThenBlock ifThenBlock = addIfThenBlock(elem);
				itbList.put(entry.getKey(), ifThenBlock);
			// BOT Action
			} else if (nodeType.equals("Bot Action")) {
				ServiceFunction sf = addAction(entry.getKey(), elem, config);
				bsfList.put(entry.getKey(), sf);
			// User Action
			} else if (nodeType.equals("User Action")) {
				ServiceFunction sf = addAction(entry.getKey(), elem, config);
				usfList.put(entry.getKey(), sf);
			} else if (nodeType.equals("Action Parameter")) {
				ServiceFunctionAttribute sfa = addActionParameter(entry.getKey(), elem);
				sfaList.put(entry.getKey(), sfa);
			} else if (nodeType.equals("TextToText") || nodeType.equals("Classifier")) {
				ContentGenerator g = new ContentGenerator();
				g.setId(entry.getKey());
				g.setName(nodeType);
				if (nodeType.equals("TextToText")) {
					g.setServiceName(textToTextName);
				} else if (nodeType.equals("Classifier")) {
					g.setServiceName(classifierName);
				}
				gList.put(entry.getKey(), g);
			}
		}

		if (vleCount != 1) {
			throw new ParseBotException("There must only be one VLE instance!");
		} else if (users.size() == 0 && bots.size() == 0 ) {
			throw new ParseBotException("Missing VLE User!");
		} else if (bsfList.size() == 0) {
			throw new ParseBotException("Missing Bot Action!");
		} else if (usfList.size() == 0 && rlist.size() == 0) {
			throw new ParseBotException("Missing User Action or VLE Routine!");
		}

		vle.setRoutines(rlist);

		if (bots.size() == 0) {
			throw new ParseBotException("Missing Bot!");
		}

		int checkGeneratorIns = 0;
		int checkGeneratorOuts = 0;

		// EDGES
		for (Entry<String, BotModelEdge> entry : edges.entrySet()) {
			BotModelEdge elem = entry.getValue();
			String type = elem.getType();
			String source = elem.getSource();
			String target = elem.getTarget();
			// HAS
			if (type.equals("has")) {
				// has User
				if(vles.get(source)!=null) {
					VLE v = vles.get(source);
					// has real user
					if(users.get(target)!=null) {
						
						VLEUser u = users.get(target);
						v.addUser(u.getName(), u);
						u.setVle(v);
					} else if(bots.get(target)!=null) {
						Bot b = bots.get(target);
						v.addBot(b.getId(), b);
						b.setVle(v);
					}	
				} else if(usfList.get(source)!=null) {
					// User Function has Parameter
					ServiceFunction sf = usfList.get(source);
					if(sfaList.get(target)!=null) {
						ServiceFunctionAttribute sfa = sfaList.get(target);
						sf.addAttribute(sfa);
						sfa.setFunction(sf);
					}
				} else if(bsfList.get(source)!=null) {
					// Bot Function has Parameter
					ServiceFunction sf = bsfList.get(source);
					if(sfaList.get(target)!=null) {
						ServiceFunctionAttribute sfa = sfaList.get(target);
						sf.addAttribute(sfa);
						sfa.setFunction(sf);
					}
				} else if(sfaList.get(source)!=null) {
					// Bot Function has Parameter
					ServiceFunctionAttribute sfaParent = sfaList.get(source);
					if(sfaList.get(target)!=null) {
						ServiceFunctionAttribute sfaChild = sfaList.get(target);
						sfaParent.addChildAttribute(sfaChild);
						sfaChild.setParent(sfaParent);
					}
				}
				
			// PERFORMS
			} else if (type.equals("performs")) {
				// Bot performs Action
				if(bots.get(source)!=null) {
					Bot bot = bots.get(source);
					ServiceFunction bsfListItem = bsfList.get(target);
					if (bsfListItem != null) {
						bot.addBotServiceFunction(bsfListItem.getId(), bsfListItem);
						bsfListItem.addBot(bot);
					}
				}else if(users.get(source)!=null) {
					VLEUser user = users.get(source);
					ServiceFunction bsfListItem = bsfList.get(target);
					if (bsfListItem != null) {
						user.addFunction(bsfListItem);
						bsfListItem.addUser(user);
					}
				// VLE performs Routine
				} else if(vles.get(source)!=null) {
					VLE v = vles.get(source);
					VLERoutine r = rlist.get(target);
					if (r != null) {
						v.addRoutine(target, r);
						r.setVle(v);
					}
				}
					
			// same as
			// TODO
			} else if (type.equals("same as")) {
				sfaList.get(source).setSameAsTrigger(true);
				sfaList.get(source).setMappedTo(sfaList.get(target));
				sfaList.get(target).setSameAsTrigger(true);
				sfaList.get(target).setMappedTo(sfaList.get(source));
			// USES
			} else if (type.equals("uses")) {
				if(gList.get(source)!=null) {
					checkGeneratorIns++;
					ContentGenerator g = (ContentGenerator) (gList.get(source));
					ServiceFunctionAttribute sfa = sfaList.get(target);
					
					sfa.setGenerator(g);
					g.setInput(sfa);
				} else if(itbList.get(source)!=null) {
					IfThenBlock itb = itbList.get(source);
					if(itbList.get(target)!=null) {
						// chain
						IfThenBlock prev = itbList.get(target);
						itb.setPrev(prev);
						prev.setNext(itb);
					}else if(sfaList.get(target)!=null) {
						ServiceFunctionAttribute sAtt = sfaList.get(target);
						itb.setSourceAttribute(sAtt);
					}
				}
				
			// GENERATES
			} else if (type.equals("generates")) {
				if(gList.get(source)!=null) {
					checkGeneratorOuts++;
					
					ContentGenerator g = (ContentGenerator) (gList.get(source));
					ServiceFunctionAttribute sfa = sfaList.get(target);
					
					g.setOutput(sfa);
					sfa.setGenerator(g);
				} else if(itbList.get(source)!=null) {
					IfThenBlock itb = itbList.get(source);
					if(sfaList.get(target)!=null) {
						ServiceFunctionAttribute sAtt = sfaList.get(target);
						sAtt.setItb(itb);
						itb.setTargetAttribute(sAtt);
					}
				}
			// TRIGGERS
			} 
		}
		
		// EDGES
		for (Entry<String, BotModelEdge> entry : edges.entrySet()) {
			BotModelEdge elem = entry.getValue();
			String type = elem.getType();
			String source = elem.getSource();
			String target = elem.getTarget();
			if (type.equals("triggers")) {
				// Action triggers action
				if(usfList.get(source)!=null) {
					ServiceFunction userFunction = usfList.get(source);
					if(bsfList.get(target)!=null) {
						ServiceFunction botFunction = bsfList.get(target);
						Trigger t = new Trigger(userFunction,botFunction);
						userFunction.addTrigger(t);
						for(Bot b:botFunction.getBots()) {
							b.addTrigger(t);
						}
					}
				}else if(rlist.get(source)!=null) {
					VLERoutine r = rlist.get(source);
					if(bsfList.get(target)!=null) {
						ServiceFunction botFunction = bsfList.get(target);
						Trigger t = new Trigger(r,botFunction);
						r.addTrigger(t);
					}
				}
				// Routine triggers action
			}
		}

		if (checkGeneratorIns != checkGeneratorOuts) {
			throw new ParseBotException("Check the Content Generator connections! There are " + checkGeneratorIns
					+ " inputs and " + checkGeneratorOuts + " outputs.");
		}

		// create if then structure
		//createIfThenStructure(tempitbList, ibList, tbList, itbList);

		// pass if then
		//passIfThen(config, ibList, tbList, itbList, bsfList, usfList, sfaList);

		// pass attributes
		//passAttributes(config, bsfList, usfList, sfaList);

		// pass content generators
		//passContentGenerators(config, bsfList, usfList, sfaList, gList);

		JSONArray jaf = swaggerHelperFunction(config);

		JSONObject j = new JSONObject();
		j.put("triggerFunctions", jaf);
		System.out.println(jaf.toJSONString());
		JSONArray jarr = new JSONArray();
		for (BotAgent b : botAgents.values()) {
			jarr.add(b.getIdentifier());
		}
		j.put("botIds", jarr);
		Context.get().monitorEvent(MonitoringEvent.BOT_ADD_TO_MONITORING, j.toJSONString());
	}

	private VLE setVLEInstance(BotModelNode elem) {
		VLE vle = new VLE();
		for (Entry<String, BotModelNodeAttribute> subEntry : elem.getAttributes().entrySet()) {
			BotModelNodeAttribute subElem = subEntry.getValue();
			BotModelValue subVal = subElem.getValue();
			String name = subVal.getName();
			if (name.equals("Address")) {
				vle.setAddress(subVal.getValue());
			} else if (name.equals("Name")) {
				vle.setName(subVal.getValue());
			} else if (name.equals("Environment Seperator")) {
				String sep = (String) subVal.getValue();
				if (sep.equals("")) {
					// Single Environment
					vle.setEnvironmentSeparator("singleEnvironment");
				} else {
					// normal setup
					vle.setEnvironmentSeparator(sep);
				}
			}
		}
		return vle;
	}

	private Bot addBot(BotModelNode elem, HashMap<String, BotAgent> botAgents) {
		Bot b = new Bot();
		for (Entry<String, BotModelNodeAttribute> subEntry : elem.getAttributes().entrySet()) {
			BotModelNodeAttribute subElem = subEntry.getValue();
			BotModelValue subVal = subElem.getValue();
			if (subVal.getName().equals("Name")) {
				String botName = subVal.getValue();
				BotAgent botAgent = null;
				try {
					try {
						botAgent = (BotAgent) Context.getCurrent()
								.fetchAgent(Context.getCurrent().getUserAgentIdentifierByLoginName(botName));
					} catch (AgentNotFoundException e) {
						// AgentOperationFailedException should be handled separately
						botAgent = BotAgent.createBotAgent(botPass);
						botAgent.unlock(botPass);
						botAgent.setLoginName(botName);
						Context.getCurrent().storeAgent(botAgent);
					}
					botAgent.unlock(botPass);
					Context.getCurrent().registerReceiver(botAgent);
				} catch (AgentException | CryptoException e2) {
					// TODO Errorhandling
					e2.printStackTrace();
				}
				// runningAt = botAgent.getRunningAtNode();
				System.out.println("Bot " + botName + " registered at: " + botAgent.getRunningAtNode().getNodeId());

				// config.addBot(botAgent.getIdentifier(), botAgent.getLoginName());
				b.setId(botAgent.getIdentifier());
				b.setName(botAgent.getLoginName());
				botAgents.put(botName, botAgent);
			}
		}
		return b;
	}

	private VLEUser addUser(BotModelNode elem) {
		VLEUser u = new VLEUser();
		for (Entry<String, BotModelNodeAttribute> subEntry : elem.getAttributes().entrySet()) {
			BotModelNodeAttribute subElem = subEntry.getValue();
			BotModelValue subVal = subElem.getValue();
			if (subVal.getName().equals("Role")) {
				String userType = subVal.getValue();
				u.setName(userType);
			}
		}
		return u;
	}

	private VLERoutine addRoutine(BotModelNode elem) {
		VLERoutine r = new VLERoutine();
		for (Entry<String, BotModelNodeAttribute> subEntry : elem.getAttributes().entrySet()) {
			BotModelNodeAttribute subElem = subEntry.getValue();
			BotModelValue subVal = subElem.getValue();
			String name = subVal.getName();
			if (name.equals("Name")) {
				String routineName = subVal.getValue();
				r.setName(routineName);
			} else if (name.equals("Time")) {
				String routineTime = subVal.getValue();
				r.setTime(routineTime);
			} else if (name.equals("Interval")) {
				String routineInterval = subVal.getValue();
				r.setInterval(routineInterval);
			}
		}
		return r;
	}
	
	private IfThenBlock addIfThenBlock(BotModelNode elem) {
		IfThenBlock itb = new IfThenBlock();
		for (Entry<String, BotModelNodeAttribute> subEntry : elem.getAttributes().entrySet()) {
			BotModelNodeAttribute subElem = subEntry.getValue();
			BotModelValue subVal = subElem.getValue();
			String name = subVal.getName();
			if (name.equals("Condition Type")) {
				itb.setConditionType(subVal.getValue());
			} else if (name.equals("Statement Type")) {
				itb.setStatementType(subVal.getValue());
			} else if (name.equals("Condition A")) {
				itb.setConditionValueA(subVal.getValue());
			} else if (name.equals("Condition B")) {
				itb.setConditionValueB(subVal.getValue());
			} else if (name.equals("Statement A")) {
				itb.setStatementValueA(subVal.getValue());
			} else if (name.equals("Statement B")) {
				itb.setStatementValueB(subVal.getValue());
			}
		}
		return itb;
	}

	private ServiceFunction addAction(String key, BotModelNode elem, BotConfiguration config) {

		ServiceFunction sf = new ServiceFunction();
		sf.setId(key);
		String actionType = "";
		String conversationType = "";
		String service = "";
		String sfName = "";
		String token = "";
		for (Entry<String, BotModelNodeAttribute> subEntry : elem.getAttributes().entrySet()) {
			BotModelNodeAttribute subElem = subEntry.getValue();
			BotModelValue subVal = subElem.getValue();
			String name = subVal.getName();
			if (name.equals("Function Name")) {
				sfName = subVal.getValue();
			} else if (name.equals("Service Alias")) {
				service = subVal.getValue();
			} else if (name.equals("Action Type")) {
				actionType = subVal.getValue();
			} else if (name.equals("Messenger Type")) {
				conversationType = subVal.getValue();
			} else if (name.equals("Token")) {
				token = subVal.getValue();
			}
		}

		if (actionType.equals("Messenger")) {
			sf.setActionType(ActionType.MESSENGER);
			if(conversationType.equals("Slack")) {
				Slack slack = new Slack(token);
				sf.setMessenger(slack);
			}
			sf.setServiceName(service);
		} else {
			// default case
			sf.setFunctionName(sfName);
			sf.setServiceName(service);
		}
		return sf;
	}

	private ServiceFunctionAttribute addActionParameter(String key, BotModelNode elem) {

		ServiceFunctionAttribute sfa = new ServiceFunctionAttribute();
		sfa.setId(key);
		for (Entry<String, BotModelNodeAttribute> subEntry : elem.getAttributes().entrySet()) {
			BotModelNodeAttribute subElem = subEntry.getValue();
			BotModelValue subVal = subElem.getValue();
			String name = subVal.getName();
			if (name.equals("Content Type")) {
				sfa.setContentType((String) subVal.getValue());
			} else if (name.equals("Name")) {
				sfa.setName((String) subVal.getValue());
			} else if (name.equals("Static")) {
				sfa.setStaticContent(Boolean.parseBoolean(subVal.getValue()));
				System.out.println(subVal.getValue());
			} else if (name.equals("Content")) {
				sfa.setContent(subVal.getValue());
			} else if (name.equals("URL")) {
				sfa.setContentURL(subVal.getValue());
			} else if (name.equals("Parameter Type")) {
				String pType = subVal.getValue();
				sfa.setParameterType(pType);
			}
		}
		return sfa;
	}
	

	private void addServiceInformation(ServiceFunction f, JSONObject elem) {
		// pfade
		for (HashMap.Entry<String, Object> subEntry : ((JSONObject) elem.get("paths")).entrySet()) {
			// type
			for (HashMap.Entry<String, Object> subsubEntry : ((JSONObject) subEntry.getValue()).entrySet()) {
				JSONObject functionInfo = (JSONObject) subsubEntry.getValue();
				String opId = (String) functionInfo.get("operationId");
				JSONArray consumes = (JSONArray) functionInfo.get("consumes");
				JSONArray produces = (JSONArray) functionInfo.get("produces");
				if (opId.toLowerCase().equals(f.getFunctionName().toLowerCase())) {
					f.setFunctionPath(subEntry.getKey());
					f.setHttpMethod(subsubEntry.getKey());
					if (consumes == null) {
						f.setConsumes("text/html");
					} else {
						f.setConsumes(consumes.get(0).toString());
					}
					if (produces == null) {
						f.setProduces("text/html");
					} else {
						f.setProduces(produces.get(0).toString());
					}
				}
			}
		}
	}

	private JSONArray swaggerHelperFunction(BotConfiguration config) {
		JSONArray jaf = new JSONArray();
		HashMap<String, ServiceFunction> allFunctions = new HashMap<String, ServiceFunction>();
		for (VLE vle : config.getVLEs().values()) {
			for (Bot b : vle.getBots().values()) {
				for(Trigger t:b.getTriggerList()) {
					if(t.getTriggerFunction() instanceof ServiceFunction) {
						ServiceFunction sf = (ServiceFunction)t.getTriggerFunction();
						jaf.add(sf.getFunctionName());
					}
					
				}
				
				allFunctions.putAll(b.getBotServiceFunctions());
			}
			for (ServiceFunction s : allFunctions.values()) {
				// try to get swagger information

				if (vle.getServiceInformation().get(s.getServiceName()) == null
						&& s.getActionType().equals(ActionType.SERVICE)) {
					try {
						JSONObject j = readJsonFromUrl(vle.getAddress() + "/" + s.getServiceName() + "/swagger.json");
						vle.addServiceInformation(s.getServiceName(), j);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				if (vle.getServiceInformation().get(s.getServiceName()) != null && s.getFunctionName() != null) {
					addServiceInformation(s, vle.getServiceInformation().get(s.getServiceName()));
				}
			}

		}
		return jaf;
	}

	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}

	private static JSONObject readJsonFromUrl(String url) throws IOException, ParseException {
		InputStream is = new URL(url).openStream();
		try {
			BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
			String jsonText = readAll(rd);
			JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);
			JSONObject json = (JSONObject) p.parse(jsonText);
			return json;
		} finally {
			is.close();
		}
	}
}
