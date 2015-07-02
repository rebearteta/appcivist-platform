package controllers;

import be.objectify.deadbolt.java.actions.Dynamic;
import be.objectify.deadbolt.java.actions.SubjectPresent;

import com.feth.play.module.pa.PlayAuthenticate;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import http.Headers;
import models.Assembly;
import models.Campaign;
import models.User;
import play.Logger;
import play.data.Form;
import play.i18n.Messages;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import security.SecurityModelConstants;
import utils.GlobalData;
import models.transfer.TransferMembership;
import models.transfer.TransferResponseStatus;

import java.util.List;

import static play.data.Form.form;

@Api(value="/campaign",description="Campaign management endpoints")
@With(Headers.class)
public class Campaigns extends Controller {

	public static final Form<Campaign> CAMPAIGN_FORM = form(Campaign.class);

	@ApiOperation(httpMethod = "GET", response = TransferMembership.class, produces = "application/json", value = "List campaigns of an Assembly")
	@Dynamic(value = "MemberOfAssembly", meta = SecurityModelConstants.ASSEMBLY_RESOURCE_PATH)
	public static Result findCampaigns(Long aid) {
		List<Campaign> campaigns = Campaign.findByAssembly(aid);
		return ok(Json.toJson(campaigns));
	}

	// TODO create dynamic auth handler that controlls the campaigns belong to
	// the specified assembly
	// that's why assembly id is always a parameter
	@SubjectPresent
	public static Result findCampaign(Long aid, Long campaignId) {
		Campaign campaign = Campaign.read(campaignId);
		return ok(Json.toJson(campaign));
	}

	@SubjectPresent
	public static Result deleteCampaign(Long aid, Long campaignId) {
		Campaign.delete(campaignId);
		return ok();
	}

	@SubjectPresent
	public static Result updateCampaign(Long aid, Long campaignId) {
		// 1. read the campaign data from the body
		// another way of getting the body content => request().body().asJson()
		final Form<Campaign> newCampaignForm = CAMPAIGN_FORM.bindFromRequest();

		if (newCampaignForm.hasErrors()) {
			TransferResponseStatus responseBody = new TransferResponseStatus();
			responseBody.setStatusMessage(Messages.get(
					GlobalData.CAMPAIGN_CREATE_MSG_ERROR,
					newCampaignForm.errorsAsJson()));
			return badRequest(Json.toJson(responseBody));
		} else {
			Campaign newCampaign = newCampaignForm.get();
			TransferResponseStatus responseBody = new TransferResponseStatus();
			if (Campaign.readByTitle(newCampaign.getTitle()) > 0) {
				Logger.info("Campaign already exists");
			} else {
				newCampaign.setCampaignId(campaignId);
				newCampaign.update();
				Logger.info("Updating campaign");
				Logger.debug("=> " + newCampaignForm.toString());
				responseBody.setNewResourceId(newCampaign.getCampaignId());
				responseBody.setStatusMessage(Messages.get(
						GlobalData.CAMPAIGN_CREATE_MSG_SUCCESS,
						newCampaign.getTitle()));
				responseBody.setNewResourceURL(GlobalData.CAMPAIGN_BASE_PATH
						+ "/" + newCampaign.getCampaignId());
			}
			return ok(Json.toJson(responseBody));
		}
	}

	@SubjectPresent
	public static Result createCampaign(Long aid) {
		// 1. obtaining the user of the requestor
		User campaignCreator = User.findByAuthUserIdentity(PlayAuthenticate
				.getUser(session()));
		// 2. read the new campaign data from the body
		// another way of getting the body content => request().body().asJson()
		final Form<Campaign> newCampaignForm = CAMPAIGN_FORM.bindFromRequest();

		if (newCampaignForm.hasErrors()) {
			TransferResponseStatus responseBody = new TransferResponseStatus();
			responseBody.setStatusMessage(Messages.get(
					GlobalData.CAMPAIGN_CREATE_MSG_ERROR,
					newCampaignForm.errorsAsJson()));
			return badRequest(Json.toJson(responseBody));
		} else {
			Campaign newCampaign = newCampaignForm.get();
			newCampaign.setAssembly(Assembly.read(aid));
			if (newCampaign.getLang() == null)
				newCampaign.setLang(campaignCreator.getLanguage());
			TransferResponseStatus responseBody = new TransferResponseStatus();
			if (Campaign.readByTitle(newCampaign.getTitle()) > 0) {
				Logger.info("Campaign already exists");
			} else {
				Campaign.create(newCampaign);
				Logger.info("Creating new campaign");
				Logger.debug("=> " + newCampaignForm.toString());
				responseBody.setNewResourceId(newCampaign.getCampaignId());
				responseBody.setStatusMessage(Messages.get(
						GlobalData.CAMPAIGN_CREATE_MSG_SUCCESS,
						newCampaign.getTitle()));
				responseBody.setNewResourceURL(GlobalData.CAMPAIGN_BASE_PATH
						+ "/" + newCampaign.getCampaignId());
			}
			return ok(Json.toJson(responseBody));
		}
	}
}