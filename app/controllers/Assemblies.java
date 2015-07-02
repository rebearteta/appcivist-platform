package controllers;

import static play.data.Form.form;

import java.util.List;

import http.Headers;
import models.Assembly;
import models.User;
import models.transfer.TransferMembership;
import models.transfer.TransferResponseStatus;
import play.Logger;
import play.data.Form;
import play.i18n.Messages;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import play.mvc.With;
import security.SecurityModelConstants;
import utils.GlobalData;
import be.objectify.deadbolt.java.actions.Dynamic;
import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;
import be.objectify.deadbolt.java.actions.SubjectPresent;

import com.feth.play.module.pa.PlayAuthenticate;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import enums.ResponseStatus;

@Api(value = "/assembly", description = "Assembly Making basic services: creating assemblies, listing assemblies, creating groups and invite people to join")
@With(Headers.class)
public class Assemblies extends Controller {

	public static final Form<Assembly> ASSEMBLY_FORM = form(Assembly.class);
	public static final Form<TransferMembership> MEMBERSHIP_FORM = form(TransferMembership.class);

	/**
	 * Return the full list of assemblies
	 * 
	 * @return models.AssemblyCollection
	 */
	@ApiOperation(httpMethod = "GET", response = Assembly.class, produces = "application/json", value = "Get list of assemblies", notes = "Get the full list of assemblies. Only availabe to ADMINS")
	@Restrict({ @Group(GlobalData.ADMIN_ROLE) })
	public static Result findAssemblies() {
		List<Assembly> assemblies = Assembly.findAll();
		return ok(Json.toJson(assemblies));
	}

	/**
	 * Return queries based on a query
	 * 
	 * @return models.AssemblyCollection
	 */
	@Restrict({ @Group(GlobalData.USER_ROLE) })
	public static Result searchAssemblies(String query) {
		// check the user who is accepting the invitation is
		// TODO
		return notFound(Json.toJson(TransferResponseStatus.noDataMessage("Not implemented yet", "")));
	}

	@ApiOperation(response = Assembly.class, produces = "application/json", value = "Create a new assembly")
	@ApiResponses(value = { @ApiResponse(code = 400, message = "Errors in the form", response = TransferResponseStatus.class) })
	@Restrict({ @Group(GlobalData.USER_ROLE) })
	public static Result createAssembly() {
		// 1. obtaining the user of the requestor
		User groupCreator = User.findByAuthUserIdentity(PlayAuthenticate
				.getUser(session()));

		// 2. read the new group data from the body
		// another way of getting the body content => request().body().asJson()
		final Form<Assembly> newAssemblyForm = ASSEMBLY_FORM.bindFromRequest();

		if (newAssemblyForm.hasErrors()) {
			return badRequest(Json.toJson(TransferResponseStatus.badMessage(
					Messages.get(GlobalData.ASSEMBLY_CREATE_MSG_ERROR,
							newAssemblyForm.errorsAsJson()), newAssemblyForm
							.errorsAsJson().toString())));
		} else {
			Assembly newAssembly = newAssemblyForm.get();

			// setting default values (TODO: maybe we should create a dedicated
			// method for this in each model)
			newAssembly.setCreator(groupCreator);
			if (newAssembly.getLang() == null)
				newAssembly.setLang(groupCreator.getLanguage());
			// TODO: check if assembly with same title exists
			// if not add it
			newAssembly.save();
			// TODO: return URL of the new group
			Logger.info("Creating assembly");
			Logger.debug("=> " + newAssemblyForm.toString());
			return ok(Json.toJson(newAssembly));
		}
	}

	@Dynamic(value = "MemberOfAssembly", meta = SecurityModelConstants.ASSEMBLY_RESOURCE_PATH)
	public static Result findAssembly(Long id) {
		Assembly a = Assembly.read(id);
		return a != null ? ok(Json.toJson(a)) : notFound(Json
				.toJson(new TransferResponseStatus(ResponseStatus.NODATA,
						"No assembly with ID = " + id)));
	}

	@Dynamic(value = "CoordinatorOfAssembly", meta = SecurityModelConstants.ASSEMBLY_RESOURCE_PATH)
	public static Result deleteAssembly(Long id) {
		Assembly.delete(id);
		return ok();
	}

	@SubjectPresent
	public static Result updateAssembly(Long id) {
		// 1. read the new group data from the body
		// another way of getting the body content => request().body().asJson()
		final Form<Assembly> newAssemblyForm = ASSEMBLY_FORM.bindFromRequest();

		if (newAssemblyForm.hasErrors()) {
			TransferResponseStatus responseBody = new TransferResponseStatus();
			responseBody.setStatusMessage(Messages.get(
					GlobalData.ASSEMBLY_CREATE_MSG_ERROR,
					newAssemblyForm.errorsAsJson()));
			return badRequest(Json.toJson(responseBody));
		} else {
			Assembly newAssembly = newAssemblyForm.get();

			TransferResponseStatus responseBody = new TransferResponseStatus();
			newAssembly.setAssemblyId(id);
			newAssembly.update();

			// TODO: return URL of the new group
			Logger.info("Updating assembly");
			Logger.debug("=> " + newAssemblyForm.toString());

			responseBody.setNewResourceId(newAssembly.getAssemblyId());
			responseBody.setStatusMessage(Messages.get(
					GlobalData.ASSEMBLY_CREATE_MSG_SUCCESS,
					newAssembly.getName()));
			responseBody.setNewResourceURL(GlobalData.ASSEMBLY_BASE_PATH + "/"
					+ newAssembly.getAssemblyId());

			return ok(Json.toJson(responseBody));
		}
	}

	@SubjectPresent
	public static Result createAssemblyMembership(Long id, String type) {
		// 1. obtaining the user of the requestor
		User requestor = User.findByAuthUserIdentity(PlayAuthenticate
				.getUser(session()));

		// 2. read the new group data from the body
		// another way of getting the body content => request().body().asJson()
		final Form<TransferMembership> newMembershipForm = MEMBERSHIP_FORM
				.bindFromRequest();

		if (newMembershipForm.hasErrors()) {
			TransferResponseStatus responseBody = new TransferResponseStatus();
			responseBody.setStatusMessage(Messages.get(
					GlobalData.MEMBERSHIP_INVITATION_CREATE_MSG_ERROR,
					newMembershipForm.errorsAsJson()));
			return badRequest(Json.toJson(responseBody));
		} else {
			TransferMembership newMembership = newMembershipForm.get();
			return Memberships.createMembership(requestor, "assembly", id,
					type, newMembership.getUserId(), newMembership.getEmail(),
					newMembership.getDefaultRoleId(),
					newMembership.getDefaultRoleName());
		}
	}

	// TODO GET /api/assembly/:id/membership/:status
	// controllers.Assemblies.listMembershipsWithStatus(id: Long, status:
	// String)
	@Security.Authenticated(Secured.class)
	public static Result listMemberships(Long id) {
		// check the user who is accepting the invitation is
		// TODO
		TransferResponseStatus responseBody = new TransferResponseStatus();
		responseBody.setStatusMessage("Not implemented yet");
		return notFound(Json.toJson(responseBody));
	}

	@Security.Authenticated(Secured.class)
	public static Result listMembershipsWithStatus(Long id, String status) {
		// check the user who is accepting the invitation is
		// TODO
		TransferResponseStatus responseBody = new TransferResponseStatus();
		responseBody.setStatusMessage("Not implemented yet");
		return notFound(Json.toJson(responseBody));
	}
}