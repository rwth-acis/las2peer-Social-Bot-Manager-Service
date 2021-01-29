package i5.las2peer.services.socialBotManagerService.parser.openapi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import i5.las2peer.services.socialBotManagerService.dialogue.InputType;
import i5.las2peer.services.socialBotManagerService.model.Frame;
import i5.las2peer.services.socialBotManagerService.model.ServiceFunction;
import i5.las2peer.services.socialBotManagerService.model.ServiceFunctionAttribute;
import i5.las2peer.services.socialBotManagerService.model.Slot;
import i5.las2peer.services.socialBotManagerService.parser.ParseBotException;

/**
 * Handles the integration of service functions and frames.
 *
 */
public class FrameMapper {

	/**
	 * Creates a new frame consistent with a service function
	 * 
	 * @param serviceURL
	 * @param operationID
	 * @return new frame
	 * @throws ParseBotException
	 */
	public Frame create(String serviceURL, String operationID) throws ParseBotException {

		assert serviceURL != null;
		assert operationID != null;

		Frame frame = new Frame();
		ServiceFunction action = new ServiceFunction();
		action.setServiceName(serviceURL);
		action.setFunctionName(operationID);
		return this.map(action, frame);

	}

	/**
	 * Matches a frame with a service function. Generates missing slots for the
	 * frame consistent with the service function attributes.
	 * 
	 * @param modelAction modeled service action
	 * @param frame       modeled frame
	 * @return matched frame
	 * @throws ParseBotException
	 */
	public Frame map(ServiceFunction modelAction, Frame frame) throws ParseBotException {

		assert modelAction != null;
		assert frame != null;

		System.out
				.println("Mapping service action " + modelAction.getFunctionName() + " into frame " + frame.getName());

		// Merge model function definition and swagger received definition
		ServiceFunction swaggerAction = OpenAPIConnector.readFunction(modelAction);
		ServiceFunction frameAction = swaggerAction.merge(modelAction);
		frame.setServiceFunction(frameAction);

		// map regular attributes and slots
		for (ServiceFunctionAttribute attr : frameAction.getAttributes()) {

			Slot slot = map(attr, attr.getName());
			frame.addSlot(slot);

		}

		// collect additional helper attributes
		Collection<ServiceFunctionAttribute> additionalAttrs = new ArrayList<>();
		for (ServiceFunctionAttribute attr : frameAction.getAllAttributes()) {

			if (attr.hasDynamicEnums()) {
				System.out.println("mapping attribute " + attr.getName() + " is dynamic");

				System.out.println(
						"attr: size:" + attr.getAllAttributes().size() + " open" + attr.getOpenAttributes().size());

				for (ServiceFunctionAttribute addAttr : attr.getOpenAttributes()) {

					if (addAttr.isFrameGenerated()) {

						// filled by existing attribute
						Slot slot = frame.getSlot(addAttr.getSlotName());
						if (slot == null)
							throw new ParseBotException(
									"slot " + addAttr.getSlotName() + " not exists in frame " + frame.getName());

						addAttr.setSlotID(slot.getID());
						System.out.println("info attribute " + addAttr.getName() + " filled by existing attribute "
								+ addAttr.getSlotName());

					} else {

						// filled by new helper attribute
						ServiceFunctionAttribute helperAttr = new ServiceFunctionAttribute(UUID.randomUUID().toString(),
								addAttr.getName(), ParameterType.HELPER);
						helperAttr.setRequired(false);
						helperAttr.setContent(null);
						addAttr.setSlotID(helperAttr.getId());
						additionalAttrs.add(helperAttr);
						System.out.println("new helper attribute " + helperAttr.getName() + " " + helperAttr.getId());
						System.out.println("info attribute " + addAttr.getName() + " filled by " + addAttr.getSlotID());
					}

				}

			}
		}

		// map additional attributes
		for (ServiceFunctionAttribute attr : additionalAttrs) {

			Slot slot = map(attr, attr.getName());
			System.out.println("mapping additional attr " + attr.getId());
			frame.addSlot(slot);
		}

		// Fill missing frame definitions
		if (frame.getIntent() == null)
			frame.setIntent(frameAction.getFunctionName());

		if (frame.getMessage() == null)
			frame.setMessage(frameAction.getFunctionDescription());

		frame.invariant();
		return frame;
	}

	/**
	 * Maps a service function attribute into a new slot.
	 * 
	 * @param attr of service function
	 * @param name of new slot
	 * @return generated slot
	 */
	public Slot map(ServiceFunctionAttribute attr, String name) {

		assert attr != null : "map attr with slot: attr is null";
		System.out.println("mapping: " + attr.getName() + " ");

		if (attr.isArray() && attr.getName().contains("amples")) {
			System.out.println("set min items " + attr.getName());
			attr.setMinItems(3);
		}

		// create slot
		Slot slot = new Slot(name);
		slot.setParameter(attr);
		slot.setRequired(attr.isRequired());

		// content type
		if (attr.getContentType() != null)
			switch (attr.getContentType().toLowerCase()) {
			case "enum":
				slot.setInputType(InputType.Enum);
				break;
			case "integer":
				slot.setInputType(InputType.Number);
				break;
			case "url":
				slot.setInputType(InputType.Url);
				break;
			default:
				slot.setInputType(InputType.Free);
			}
		else
			slot.setInputType(InputType.Free);

		if (attr.isFile())
			slot.setInputType(InputType.File);

		if (attr.getEntity() != null)
			slot.setEntity(attr.getEntity());

		// selection
		if (attr.hasDiscriminator()) {

			System.out.println(" dis: " + attr.getDiscriminator() + " " + attr.getDiscriminatorAttribute().getName());

			slot.setSelection(false);
			ServiceFunctionAttribute disAttr = attr.getDiscriminatorAttribute();
			Slot disSlot = map(disAttr, name + "_" + disAttr.getName());
			disSlot.setSelection(true);
			disSlot.setRequired(true);
			Map<String, Slot> inheritedSlots = new HashMap<>();
			
			for (String enu : disAttr.getEnumList()) {
				Slot enuSlot = new Slot(name + "_" + disAttr.getName() + "_" + enu);
				enuSlot.setEntity(enu);
				disSlot.addChild(enuSlot);

				
				// parent class slots
				for (ServiceFunctionAttribute subAttr : attr.getChildren(disAttr.getName())) {
					if (subAttr != disAttr) {
						Slot subSlot = map(subAttr, name + "_" + disAttr.getName() + "_" + subAttr.getName());
						subSlot.setPriority(-1);
						System.out.println(
								"inherited attribute detected: " + subSlot.getName() + " " + subSlot.getPriority());
						inheritedSlots.put(subSlot.getName(), subSlot);
					}
				}
				
				// sub class slots
				for (ServiceFunctionAttribute subAttr : attr.getChildren(enu)) {
					Slot subSlot = map(subAttr, name + "_" + disAttr.getName() + "_" + enu + "_" + subAttr.getName());
					enuSlot.addChild(subSlot);
				}

			}
			
			for (Slot subSlot : inheritedSlots.values()) {
				disSlot.addChild(subSlot);
				System.out.println("add inherited slot " + subSlot.getName() + " to " + disSlot.getName());
			}

			slot.addChild(disSlot);
		}

		// children
		else if (attr.hasChildren()) {
			for (ServiceFunctionAttribute subAttr : attr.getChildAttributes()) {
				Slot subSlot = map(subAttr, name + "_" + subAttr.getName());
				slot.addChild(subSlot);
			}
		}

		return slot;
	}

}
