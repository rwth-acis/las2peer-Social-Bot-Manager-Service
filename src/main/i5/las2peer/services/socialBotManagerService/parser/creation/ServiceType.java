package i5.las2peer.services.socialBotManagerService.parser.creation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "accesstype", visible = true)
@JsonSubTypes({ @JsonSubTypes.Type(value = Las2peer.class, name = "las2peer"),
		@JsonSubTypes.Type(value = OpenAPI.class, name = "OpenAPI") })
@ApiModel(discriminator = "accesstype", subTypes = { Las2peer.class, OpenAPI.class })
public class ServiceType {
	@ApiModelProperty(dataType = "string", allowableValues = "las2peer, OpenAPI", required = true, value = "Which type of service the bot should access?")
	ServiceAccessType accesstype;

	public ServiceAccessType getAccesstype() {
		return accesstype;
	}

	public void setAccesstype(ServiceAccessType accesstype) {
		this.accesstype = accesstype;
	}
}
