package models;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

import com.avaje.ebean.ExpressionList;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import enums.ResourceSpaceTypes;

@Entity
public class Campaign extends AppCivistBaseModel {

	@Id
	@GeneratedValue
	@Column(name="campaign_id")
	private Long campaignId;
	private String title; // e.g., "PB for Vallejo 2015"
	private String shortname;
	private String goal;	
	private String url;
	private UUID uuid = UUID.randomUUID();
	@Transient
	private String uuidAsString;
	// If the assembly is listed, is basic profile is reading accessible by all 
	private Boolean listed = true;

	// Relationships	
	@OneToOne(fetch=FetchType.LAZY, cascade=CascadeType.ALL)
	@JsonIgnoreProperties({"uuid"})
	@JsonInclude(Include.NON_EMPTY)
	private ResourceSpace resources;

	@ManyToOne(cascade = CascadeType.ALL)
	private CampaignTemplate template;
	
	/**
	 * The find property is an static property that facilitates database query
	 * creation
	 */
	public static Finder<Long, Campaign> find = new Finder<>(Campaign.class);

	public Campaign() {
		super();
		this.uuid =  UUID.randomUUID(); 
		this.resources = new ResourceSpace(ResourceSpaceTypes.CAMPAIGN);
	}

	public Campaign(String title, Date startDate, Date endDate, Boolean active,
			String url, CampaignTemplate template) {
		super();
		this.title = title;
		this.url = url;
		this.template = template;
		this.uuid =  UUID.randomUUID(); 
		this.resources = new ResourceSpace(ResourceSpaceTypes.CAMPAIGN);

		// automatically populate the phases based on the campaign type
		if (template != null && template.getDefaultComponents() != null) {
			List<Component> defaultPhases = template.getDefaultComponents();

			for (Component phaseDefinition : defaultPhases) {
				ComponentInstance phase = new ComponentInstance(this, phaseDefinition);
				this.addComponent(phase);
			}
		}
	}

	public Campaign(String title, Date startDate, Date endDate, Boolean active,
			String url, CampaignTemplate type,
			List<Config> configs) {
		super();
		this.title = title;
		this.url = url;
		this.template = type;

		this.uuid =  UUID.randomUUID(); 
		this.resources = new ResourceSpace(ResourceSpaceTypes.CAMPAIGN);

		// automatically populate the phases based on the campaign type
		if (type != null && type.getDefaultComponents() != null) this.populateDefaultPhases(type.getDefaultComponents());
	}

	public Campaign(String title, String shortname, Boolean listed, CampaignTemplate type,
String uuidAsString, List<ComponentInstance> phases) {
		super();
		this.title = title;
		this.shortname = shortname;
		this.listed = listed;
		this.template = type;
		this.uuidAsString = uuidAsString;
		this.uuid =  UUID.fromString(uuidAsString);
		this.resources = new ResourceSpace(ResourceSpaceTypes.CAMPAIGN);

		// automatically populate the phases based on the campaign type
		if (type != null && type.getDefaultComponents() != null) this.populateDefaultPhases(type.getDefaultComponents());
	}
	

	/*
	 * Getters and Setters
	 */

	public Long getCampaignId() {
		return campaignId;
	}

	public void setCampaignId(Long campaignId) {
		this.campaignId = campaignId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getShortname() {
		return shortname;
	}

	public void setShortname(String shortname) {
		this.shortname = shortname;
	}

	public String getGoal() {
		return goal;
	}

	public void setGoal(String goal) {
		this.goal = goal;
	}

	public Boolean getActive() {
		return getStartDate().before(Calendar.getInstance().getTime()) || getStartDate().equals(Calendar.getInstance().getTime()) ;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public UUID getUuid() {
		return uuid;
	}

	public void setUuid(UUID uuid) {
		this.uuid = uuid;
	}

	public String getUuidAsString() {
		return uuid.toString();
	}

	public void setUuidAsString(String uuidAsString) {
		this.uuidAsString = uuidAsString;
		this.uuid = UUID.fromString(uuidAsString);
	}

	public ResourceSpace getResources() {
		return resources;
	}

	public void setResources(ResourceSpace resources) {
		this.resources = resources;
	}

	public ResourceSpace getAssemblyResourceSet() {
		return resources;
	}

	public void setAssemblyResourceSet(ResourceSpace assembly) {
		this.resources = assembly;
	}

	public CampaignTemplate getType() {
		return template;
	}

	public void setType(CampaignTemplate type) {
		this.template = type;
	}

	public static List<Campaign> findAll() {
		List<Campaign> campaigns = find.all();
		return campaigns;
	}

	public static List<Campaign> findByAssembly(Long aid) {
		return find.where().eq("assembly.assemblyId", aid).findList();
	}
	
	private void addComponent(ComponentInstance phase) {
		this.resources.getComponents().add(phase);
	}

	@JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm a z")
	private Date getStartDate() {
		List<ComponentInstance> components = this.resources.getComponents(); 
		if (components != null) {
			Collections.sort(components,new ComponentInstance());
			ComponentInstance firstPhase = components.get(0);
			return firstPhase.getStartDate();
		}
		return null;
	}

	@JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm a z")
	private Date getEndDate() {
		List<ComponentInstance> components = this.resources.getComponents(); 
		if (components != null) {
			Collections.sort(components,new ComponentInstance());
			ComponentInstance lastPhase = components.get(components.size()-1);
			return lastPhase.getEndDate();
		}
		return null;
	}
		
	public Boolean getListed() {
		return listed;
	}

	public void setListed(Boolean listed) {
		this.listed = listed;
	}

	/*
	 * Basic Data Operations
	 */
	public static void create(Campaign campaign) {
		campaign.save();
		campaign.refresh();
	}

	public static Campaign read(Long campaignId) {
		return find.ref(campaignId);
	}

	public static Integer readByTitle(String campaignTitle) {
		ExpressionList<Campaign> campaigns = find.where().eq("title",campaignTitle);
		return campaigns.findList().size();
	}

	public static Campaign createObject(Campaign campaign) {
		campaign.save();
		return campaign;
	}

	public static void delete(Long id) {
		find.ref(id).delete();
	}

	public static Campaign update(Campaign c) {
		c.update();
		c.refresh();
		return c;
	}
	
	
	private void populateDefaultPhases(List<Component> defaultPhaseDefinitions) {
		List<Component> defaultPhases = this.template.getDefaultComponents();
		for (Component phaseDefinition : defaultPhases) {
			ComponentInstance phase = new ComponentInstance(this, phaseDefinition);
			this.addComponent(phase);
		}
	}

	public static List<Campaign> extractOngoingCampaignsFromAssembly(Assembly a) {
		List<Campaign> ongoingCampaigns = new ArrayList<Campaign>();
		ResourceSpace resources = a.getResources();
		List<Campaign> campaigns = null;
		if (resources !=null) campaigns = resources.getCampaigns();
		if (campaigns != null && !campaigns.isEmpty()) {
			for (Campaign c : campaigns) {
				List<ComponentInstance> phases = c.getResources().getComponents();
				if (phases != null && !phases.isEmpty()) {
					for (ComponentInstance p : phases) {
						Calendar today = Calendar.getInstance();
						if (p.getStartDate().before(today.getTime()) && p.getEndDate().after(today.getTime())) {
							ongoingCampaigns.add(c);
							break;
						}
					}
				}
			}
		}
		return ongoingCampaigns;
	}
	
}
