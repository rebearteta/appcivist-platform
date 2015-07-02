package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.avaje.ebean.ExpressionList;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
public class CampaignPhase extends AppCivistBaseModel {

	@Id
	@GeneratedValue
	private Long phaseId;
	private Date startDate;
	private Date endDate;

	@ManyToOne
	@JsonBackReference
	private Campaign campaign;

	@OneToOne
	private PhaseDefinition definition;

	@JsonIgnore
	@OneToMany(cascade=CascadeType.ALL, mappedBy="campaignPhase")
	@JsonManagedReference
	private List<Config> campaignPhaseConfigs = new ArrayList<Config>();

	private Boolean canOverlap = false;
	
	/**
	 * The find property is an static property that facilitates database query creation
	 */
	public static Finder<Long, CampaignPhase> find = new Finder<Long, CampaignPhase>(
			Long.class, CampaignPhase.class);

	public CampaignPhase() {
		super();
	}

	public CampaignPhase(Campaign c, PhaseDefinition definition) {
		super();
		this.campaign = c;
		this.definition = definition;
	}
	
	public CampaignPhase(Date startDate, Date endDate, Campaign campaign,
			PhaseDefinition definition, List<Config> campaignPhaseConfigs) {
		super();
		this.startDate = startDate;
		this.endDate = endDate;
		this.campaign = campaign;
		this.definition = definition;
		this.campaignPhaseConfigs = campaignPhaseConfigs;
	}

	public CampaignPhase(Campaign c, PhaseDefinition definition,
			List<Config> configs) {
		super();
		this.campaign = c;
		this.definition = definition;
		this.campaignPhaseConfigs = configs;
	}

	public Long getPhaseId() {
		return phaseId;
	}

	public void setPhaseId(Long phaseId) {
		this.phaseId = phaseId;
	}

	public Date getStartDate() {
		return startDate;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

	public Campaign getCampaign() {
		return campaign;
	}

	public void setCampaign(Campaign campaign) {
		this.campaign = campaign;
	}

	public PhaseDefinition getDefinition() {
		return definition;
	}

	public void setDefinition(PhaseDefinition definition) {
		this.definition = definition;
	}

	public List<Config> getCampaignPhaseConfigs() {
		return campaignPhaseConfigs;
	}

	public void setCampaignPhaseConfigs(List<Config> campaignPhaseConfigs) {
		this.campaignPhaseConfigs = campaignPhaseConfigs;
	}

	/*
	 * Basic Data operations
	 */
	
	public Boolean getCanOverlap() {
		return canOverlap;
	}

	public void setCanOverlap(Boolean canOverlap) {
		this.canOverlap = canOverlap;
	}

	public static CampaignPhase read(Long campaignId, Long phaseId) {
		ExpressionList<CampaignPhase> campaignPhases = find.where()
				.eq("campaign.campaignId",campaignId)
				.eq("phaseId", phaseId);
		CampaignPhase phase = campaignPhases.findUnique();
		return phase;
    }

    public static List<CampaignPhase> findAll(Long campaignId) {
//		ExpressionList<CampaignPhase> campaignPhases = find.where().eq("campaign_campaign_id",campaignId);
		ExpressionList<CampaignPhase> campaignPhases = find.where()
				.eq("campaign.campaignId",campaignId);
		List<CampaignPhase> campaignPhaseList = campaignPhases.findList();
		return campaignPhaseList;
    }

    public static List<CampaignPhase> findByAssemblyAndCampaign(Long aid, Long campaignId) {
		ExpressionList<CampaignPhase> campaignPhases = find.where()
				.eq("campaign.campaignId",campaignId)
				.eq("campaign.assembly.assemblyId", aid);
		List<CampaignPhase> campaignPhaseList = campaignPhases.findList();
		return campaignPhaseList;
    }

    public static CampaignPhase create(Long campaignId, CampaignPhase phase) {
        Campaign campaign = Campaign.read(campaignId);
		PhaseDefinition phaseDefinition = null;
		if(phase.getDefinition().getPhaseDefinitionId() != null){
			phaseDefinition = PhaseDefinition.read(phase.getDefinition().getPhaseDefinitionId());
		}
		else if(phase.getDefinition().getName() != null){
			phaseDefinition = PhaseDefinition.readByName(phase.getDefinition().getName());
		}
		phase.setCampaign(campaign);
		phase.setDefinition(phaseDefinition);
		phase.save();
        phase.refresh();
        return phase;
    }

    public static CampaignPhase createObject(CampaignPhase object) {
        object.save();
        return object;
    }

    public static void delete(Long campaignId, Long phaseId) {
		ExpressionList<CampaignPhase> campaignPhases = find.where().eq("campaign_campaign_id", campaignId).eq("phase_id",phaseId);
		CampaignPhase phase = campaignPhases.findUnique();
		phase.delete();
    }

    public static CampaignPhase update(CampaignPhase cp) {
        cp.update();
        cp.refresh();
        return cp;
    }
}
