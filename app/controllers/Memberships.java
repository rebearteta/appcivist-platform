package controllers;

import static play.data.Form.form;
import http.Headers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import models.Assembly;
import models.Membership;
import models.MembershipAssembly;
import models.MembershipGroup;
import models.MembershipInvitation;
import models.SecurityRole;
import models.TokenAction;
import models.TokenAction.Type;
import models.User;
import models.WorkingGroup;
import models.transfer.InvitationTransfer;
import models.transfer.MembershipTransfer;
import models.transfer.TransferResponseStatus;
import play.Logger;
import play.Play;
import play.data.Form;
import play.i18n.Messages;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import play.mvc.With;
import providers.MyUsernamePasswordAuthProvider;
import providers.MyUsernamePasswordAuthProvider.MySignup;
import security.SecurityModelConstants;
import utils.GlobalData;
import utils.Pair;
import be.objectify.deadbolt.java.actions.Dynamic;
import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;
import ch.qos.logback.core.subst.Token;

import com.avaje.ebean.Ebean;
import com.feth.play.module.pa.PlayAuthenticate;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiImplicitParam;
import com.wordnik.swagger.annotations.ApiImplicitParams;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import delegates.MembershipsDelegate;
import enums.MembershipStatus;
import enums.MembershipTypes;
import enums.MyRoles;
import enums.ResponseStatus;

@Api(value = "/membership", description = "Group Management endpoints in the Assembly Making service")
@With(Headers.class)
public class Memberships extends Controller {

	public static final Form<MembershipTransfer> TRANSFER_MEMBERSHIP_FORM = form(MembershipTransfer.class);
	public static final Form<InvitationTransfer> TRANSFER_INVITATION_FORM = form(InvitationTransfer.class);
	public static final Form<Membership> MEMBERSHIP_FORM = form(Membership.class);
	public static final Form<SecurityRole> ROLE_FORM = form(SecurityRole.class);

	/**
	 * The membership invitation/request timeout in seconds Defaults to 4 weeks
	 * (24 hours * 30 days * 60 minutes * 60 seconds)
	 */
	public static final Long MEMBERSHIP_EXPIRATION_TIMEOUT = new Long(
			30 * 14 * 3600);

	@ApiOperation(httpMethod = "POST", response = MembershipTransfer.class, produces = "application/json", value = "Create a membership within an Assembly or a Group")
	@ApiImplicitParams({
		@ApiImplicitParam(name = "SESSION_KEY", value = "User's session authentication key", dataType = "String", paramType = "header") })
	@Restrict({ @Group(GlobalData.ADMIN_ROLE) })
	public static Result createMembership() {
		// 1. obtaining the user of the requestor
		User requestor = User.findByAuthUserIdentity(PlayAuthenticate
				.getUser(session()));

		// 2. read the new group data from the body
		// another way of getting the body content => request().body().asJson()
		final Form<MembershipTransfer> newMembershipForm = TRANSFER_MEMBERSHIP_FORM
				.bindFromRequest();

		if (newMembershipForm.hasErrors()) {
			TransferResponseStatus responseBody = new TransferResponseStatus();
			responseBody.setStatusMessage(Messages.get(
					GlobalData.MEMBERSHIP_INVITATION_CREATE_MSG_ERROR,
					newMembershipForm.errorsAsJson()));
			return badRequest(Json.toJson(responseBody));
		} else {
			MembershipTransfer newMembership = newMembershipForm.get();
			String targetCollection = newMembership.getTargetCollection();
			Long targetCollectionId = targetCollection.toUpperCase().equals("GROUP") ? newMembership.getGroupId() : newMembership.getAssemblyId();
			
			return createMembership(requestor,
					targetCollection, targetCollectionId,
					newMembership.getType(), newMembership.getUserId(),
					newMembership.getEmail(), newMembership.getDefaultRoleId(), newMembership.getDefaultRoleName());
		}
	}

	@ApiOperation(httpMethod = "POST", response = InvitationTransfer.class, produces = "application/json", value = "Create and send and to an Assembly or a Group")
	@ApiImplicitParams({
		@ApiImplicitParam(name = "SESSION_KEY", value = "User's session authentication key", dataType = "String", paramType = "header"), 
		@ApiImplicitParam(name = "aid", value = "Assembly Id", dataType = "Long", paramType = "path") })
	@Dynamic(value = "CoordinatorOfAssembly", meta = SecurityModelConstants.ASSEMBLY_RESOURCE_PATH)
	public static Result createSendInvitationToAssembly(Long aid) {
		// 1. Obtaining the user of the requestor
		User requestor = User.findByAuthUserIdentity(PlayAuthenticate.getUser(session()));
		// 2. Read the invitation data from the body
		final Form<InvitationTransfer> newInvitationForm = TRANSFER_INVITATION_FORM.bindFromRequest();

		if (newInvitationForm.hasErrors()) {
			TransferResponseStatus responseBody = new TransferResponseStatus();
			responseBody.setStatusMessage(Messages.get(
					GlobalData.MEMBERSHIP_INVITATION_CREATE_MSG_ERROR,
					newInvitationForm.errorsAsJson()));
			return badRequest(Json.toJson(responseBody));
		} else {
			Ebean.beginTransaction();
			InvitationTransfer newInvitation;
			MembershipInvitation mi = null;
			try {
				newInvitation = newInvitationForm.get();
				newInvitation.setTargetId(aid);
				newInvitation.setTargetType("ASSEMBLY");
				mi = createAndSendInvitation(requestor,newInvitation);
			} catch (Exception e) {
				Ebean.rollbackTransaction();
				TransferResponseStatus responseBody = new TransferResponseStatus();
				responseBody.setStatusMessage(Messages.get(
						GlobalData.MEMBERSHIP_INVITATION_CREATE_MSG_ERROR,
						e.getMessage()));
				return internalServerError(Json.toJson(responseBody));
			}
			return ok(Json.toJson(mi));
		}
	}

	@ApiOperation(httpMethod = "POST", response = InvitationTransfer.class, produces = "application/json", value = "Create and send an invitation to join an Assembly or a Group to a non-AppCivist user ")
	@ApiImplicitParams({
		@ApiImplicitParam(name = "SESSION_KEY", value = "User's session authentication key", dataType = "String", paramType = "header"), 
		@ApiImplicitParam(name = "gid", value = "Working Group Id", dataType = "Long", paramType = "path") })
	@Dynamic(value = "CoordinatorOfAssembly", meta = SecurityModelConstants.ASSEMBLY_RESOURCE_PATH)
	public static Result createSendInvitationToGroup(Long gid) {
		// 1. Obtaining the user of the requestor
		User requestor = User.findByAuthUserIdentity(PlayAuthenticate.getUser(session()));
		// 2. Read the invitation data from the body
		final Form<InvitationTransfer> newInvitationForm = TRANSFER_INVITATION_FORM.bindFromRequest();

		if (newInvitationForm.hasErrors()) {
			TransferResponseStatus responseBody = new TransferResponseStatus();
			responseBody.setStatusMessage(Messages.get(
					GlobalData.MEMBERSHIP_INVITATION_CREATE_MSG_ERROR,
					newInvitationForm.errorsAsJson()));
			return badRequest(Json.toJson(responseBody));
		} else {
			Ebean.beginTransaction();
			InvitationTransfer newInvitation;
			MembershipInvitation mi = null;
			try {
				newInvitation = newInvitationForm.get();
				newInvitation.setTargetId(gid);
				newInvitation.setTargetType("GROUP");
				mi = createAndSendInvitation(requestor,newInvitation);
			} catch (Exception e) {
				Ebean.rollbackTransaction();
				TransferResponseStatus responseBody = new TransferResponseStatus();
				responseBody.setStatusMessage(Messages.get(
						GlobalData.MEMBERSHIP_INVITATION_CREATE_MSG_ERROR,
						e.getMessage()));
				return internalServerError(Json.toJson(responseBody));
			}
			return ok(Json.toJson(mi));
		}
	}
	
	@ApiOperation(httpMethod = "GET", response = MembershipInvitation.class, responseContainer="List", produces = "application/json", value = "Create and send an invitation to join an Assembly or a Group to a non-AppCivist user ")
	@ApiImplicitParams({
		@ApiImplicitParam(name = "SESSION_KEY", value = "User's session authentication key", dataType = "String", paramType = "header"), 
		@ApiImplicitParam(name = "targetId", value = "Working Group or Assembly Id", dataType = "Long", paramType = "path") , 
		@ApiImplicitParam(name = "status", value = "Invitation Status", allowableValues="INVITED, ACCEPTED", dataType = "String", paramType = "path") })
	public static Result listInvitations(Long targetId, String status) {
		List<MembershipInvitation> invitations = MembershipInvitation.findByTargetIdAndStatus(targetId, status);
		return ok(Json.toJson(invitations));
	}

	@ApiOperation(httpMethod = "GET", response = InvitationTransfer.class, produces = "application/json", value = "Create and send an invitation to join an Assembly or a Group to a non-AppCivist user ")
	@ApiImplicitParams({
		@ApiImplicitParam(name = "SESSION_KEY", value = "User's session authentication key", dataType = "String", paramType = "header"), 
		@ApiImplicitParam(name = "token", value = "Invitation Token", dataType = "java.util.UUID", paramType = "path")})
	public static Result readInvitation(UUID token) {
		TokenAction ta = TokenAction.findByToken(token.toString(), TokenAction.Type.MEMBERSHIP_INVITATION); 
		if(ta.isValid()) {
			MembershipInvitation mi = MembershipInvitation.findByToken(token);
			return ok(Json.toJson(mi));
		} else {
			TransferResponseStatus response = new TransferResponseStatus(ResponseStatus.NOTAVAILABLE, "Token is no longer valid");
			return notFound(Json.toJson(response));
		}
	}

	@ApiOperation(httpMethod = "POST", response = User.class, responseContainer="List", produces = "application/json", value = "Create and send an invitation to join an Assembly or a Group to a non-AppCivist user ")
	@ApiImplicitParams({
		@ApiImplicitParam(name = "token", value = "Working Group or Assembly Id", dataType = "Long", paramType = "path") , 
		@ApiImplicitParam(name = "response", value = "Invitation Status", allowableValues="ACCEPT, REJECT", dataType = "String", paramType = "path") })
	public static Result answerInvitation(UUID token, String response) {
		Ebean.beginTransaction();
		// 1. Verifty the token
		com.feth.play.module.pa.controllers.Authenticate.noCache(response());
		final TokenAction ta = Users.tokenIsValid(token.toString(),
				Type.MEMBERSHIP_INVITATION);
		if (ta == null) {
			return badRequest(Json.toJson(TransferResponseStatus.badMessage(
					Messages.get("playauthenticate.token.error.message"),
					"Token is null")));
		}
		ta.delete();
		// 2. Read Invitation 
		MembershipInvitation mi = MembershipInvitation.findByToken(token);
					
		if(response.equals("ACCEPT")){
			mi.setStatus(MembershipStatus.ACCEPTED);
			User newUser = null;
			if(mi.getUserId()!=null) {
				newUser = User.read(mi.getUserId());
			}
			// 3. Create the User if does not exist
			Result result = null;
			String signupEmail = "";
			if (newUser==null) {
				com.feth.play.module.pa.controllers.Authenticate.noCache(response());
				final Form<MySignup> filledForm = MyUsernamePasswordAuthProvider.SIGNUP_FORM.bindFromRequest();
				if (filledForm.hasErrors()) {
					Ebean.rollbackTransaction();
					return badRequest(Json.toJson(TransferResponseStatus.badMessage(
							Messages.get("play.authenticate.filledFromHasErrors"),
							filledForm.errorsAsJson().toString())));
				} else {
					try {
						MySignup signup = filledForm.get();
						signupEmail = signup.getEmail();
						result = MyUsernamePasswordAuthProvider.handleSignup(ctx());
					} catch (Exception e) {
						Ebean.rollbackTransaction();
						return badRequest(Json
								.toJson(TransferResponseStatus.badMessage(
										Messages.get("Error has occurred when creating user"),
										e.getMessage())));
					}
				} 
			}
			
		
			// 4. Create Membership
			try {
				if (result!=null) {
					newUser = User.findByEmail(signupEmail);
					mi.setUserId(newUser.getUserId());
				} else if (result==null && newUser == null) {
					throw new Exception("The user was not created");
				}

				if (mi.getTargetType().equals(MembershipTypes.ASSEMBLY)) {
					MembershipAssembly ma = new MembershipAssembly();
					Assembly a = Assembly.read(mi.getTargetId());
					ma.setAssembly(a);
					ma.setCreator(mi.getCreator());
					ma.setStatus(MembershipStatus.ACCEPTED);
					ma.setRoles(mi.getRoles());
					ma.setTargetUuid(a.getUuid());
					ma.setUser(newUser);
					ma.save();
					ma.refresh();
				} else {
					MembershipGroup mg = new MembershipGroup();
					WorkingGroup g = WorkingGroup.read(mi.getTargetId());
					mg.setWorkingGroup(g);
					mg.setCreator(mi.getCreator());
					mg.setStatus(MembershipStatus.ACCEPTED);
					mg.setRoles(mi.getRoles());
					mg.setTargetUuid(g.getUuid());
					mg.setUser(newUser);
					mg.save();
					mg.refresh();
				}

				// 5. Update Invitation record
				mi.update();
			} catch (Exception e) {
				return internalServerError(Json
						.toJson(TransferResponseStatus.badMessage(
								Messages.get("Error has occurred: "),
								e.getMessage())));
			}
			
			if (result!=null) {
				Ebean.commitTransaction();
				return result;
			} else {
				Ebean.commitTransaction();
				return ok(Json.toJson(newUser));
			}
		} else {
			// 5. Update Invitation record
			mi.setStatus(MembershipStatus.REJECTED);
			mi.update();	
			Ebean.commitTransaction();
			return ok(Json.toJson("DONE"));
		}
	}
	
	private static MembershipInvitation createAndSendInvitation(User creator, InvitationTransfer invitation) {
		MembershipInvitation membershipInvitation = new MembershipInvitation();
		membershipInvitation.setCreator(creator);
		membershipInvitation.setEmail(invitation.getEmail());

		// if email is in system, attach user
		User u = User.findByEmail(invitation.getEmail());
		if (u!=null) {
			membershipInvitation.setUserId(u.getUserId());
		}
		
		membershipInvitation.setLang(creator.getLanguage());
		membershipInvitation.setStatus(MembershipStatus.INVITED);
		MembershipTypes invitationType = invitation.getTargetType().equals("ASSEMBLY") ? MembershipTypes.ASSEMBLY : MembershipTypes.GROUP;
		membershipInvitation.setTargetType(invitationType );
		membershipInvitation.setTargetId(invitation.getTargetId());

		List<SecurityRole> roles = new ArrayList<>();
		roles.add(SecurityRole.findByName(MyRoles.MEMBER.getName()));

		if (invitation.getCoordinator())
			roles.add(SecurityRole.findByName(MyRoles.COORDINATOR.getName()));
		if (invitation.getModerator())
			roles.add(SecurityRole.findByName(MyRoles.MODERATOR.getName()));

		membershipInvitation.setRoles(roles);
		final String token = UUID.randomUUID().toString();
		TokenAction ta = TokenAction.create(TokenAction.Type.MEMBERSHIP_INVITATION, token, null);
		membershipInvitation.setToken(ta);
		
		String baseInvitationUrl = Play.application().configuration().getString("appcivist.invitations.baseUrl");
		String invitationUrl = baseInvitationUrl + "invitation/"+token;
		String invitationEmailText = invitation.getInvitationEmail()+"\n\n\n"+Messages.get("membership.invitation.email.link")+": "+invitationUrl;
		String invitationEmailHTML = invitation.getInvitationEmail()+"<br><br>"+"<a href='"+invitationUrl+"'>"+Messages.get("membership.invitation.email.link")+"</a>";
		membershipInvitation = MembershipInvitation.create(membershipInvitation);
		
		Logger.info("Sending group invitation to: "+membershipInvitation.getEmail());
		Logger.info("Invitation email: "+invitationEmailText);
		MyUsernamePasswordAuthProvider provider = MyUsernamePasswordAuthProvider.getProvider();
		String emailSubject = Messages.get("membership.invitation.email.subject", invitation.getTargetType());
		provider.sendInvitationByEmail(membershipInvitation, invitationEmailText, invitationEmailHTML, emailSubject);		
		return membershipInvitation;
	}

	/**
	 * Read a membership by ID
	 * 
	 * @param id
	 * @return
	 */
	@Security.Authenticated(Secured.class)
	public static Result readMembership(Long id) {
		Membership m = Membership.read(id);
		if (m != null) {
			List<SecurityRole> roles = m.getRoles();
			Logger.debug("Membership roles: #" +roles.size() +" = "+ roles.toString());
			return ok(Json.toJson(m));
		} else {
			TransferResponseStatus responseBody = new TransferResponseStatus();
			responseBody.setStatusMessage("There is no membership with ID = "
					+ id);
			return notFound(Json.toJson(responseBody));
		}
	}

	/**
	 * Read the roles assigned to a specific membership by ID
	 * 
	 * @param id
	 * @return
	 */
	@Security.Authenticated(Secured.class)
	public static Result readMembershipRoles(Long id) {
		Membership m = Membership.read(id);
		if (m != null) {
			List<SecurityRole> roles = m.getRoles();
			return roles != null ? ok(Json.toJson(roles)) : notFound(Json
					.toJson(new TransferResponseStatus(
							ResponseStatus.NOTAVAILABLE,
							"No roles for membership" + id)));
		} else {
			TransferResponseStatus responseBody = new TransferResponseStatus();
			responseBody.setStatusMessage("There is no membership with ID = "
					+ id);
			return notFound(Json.toJson(responseBody));
		}
	}

	/**
	 * Add a Role to the membership (only Coordinators of the Assembly/Group)
	 * 
	 * private Long roleId; private String name;
	 * 
	 * @param id
	 * @return
	 */
	@Security.Authenticated(Secured.class)
	public static Result addMembershipRole(Long id) {
		Membership m = Membership.read(id);
		if (m != null) {
			// TODO move all the role checking to another common place
			User requestor = User.findByAuthUserIdentity(PlayAuthenticate
					.getUser(session()));
			Boolean authorization = false;
			// what's the requestor membership in the group/assembly related to
			// this membership
			Membership requestorMembership = MembershipsDelegate.requestorMembership(requestor, m);

			if (requestorMembership != null) {
				authorization = MembershipsDelegate.requestorIsCoordinator(requestor,
						requestorMembership);
			} else {
				return unauthorized(Json.toJson(new TransferResponseStatus(
						ResponseStatus.UNAUTHORIZED,
						"Requestor is not member of this "
								+ m.getMembershipType())));
			}

			if (authorization) {
				final Form<SecurityRole> newRoleForm = ROLE_FORM.bindFromRequest();
				if (newRoleForm.hasErrors()) {
					TransferResponseStatus responseBody = new TransferResponseStatus();
					responseBody
							.setStatusMessage("There was an error in the role included in the request"
									+ newRoleForm.errorsAsJson());
					return badRequest(Json.toJson(responseBody));
				} else {
					SecurityRole newRole = newRoleForm.get();
					Long roleId = newRole.getRoleId();
					String roleName = newRole.getName();

					SecurityRole role = SecurityRole.read(roleId);
					if (role == null) {
						role = SecurityRole.findByName(roleName);
					}

					if (role != null) {
						m.getRoles().add(role);
						m.update();
						m.refresh();
						return ok(Json.toJson(m));
					} else {
						return internalServerError(Json
								.toJson(new TransferResponseStatus(
										ResponseStatus.NOTAVAILABLE,
										"The role you are trying to add ("
												+ roleName + ")"
												+ " does not exist ")));
					}
				}

			} else {
				return unauthorized(Json.toJson(new TransferResponseStatus(
						ResponseStatus.UNAUTHORIZED, "Requestor is not"
								+ MyRoles.COORDINATOR)));
			}

		} else {
			TransferResponseStatus responseBody = new TransferResponseStatus();
			responseBody.setStatusMessage("There is no membership with ID = "
					+ id);
			return notFound(Json.toJson(responseBody));
		}
	}

	/**
	 * Add a Role to the membership (only Coordinators of the Assembly/Group)
	 * 
	 * private Long roleId; private String name;
	 * 
	 * @param id
	 * @return
	 */
	@Security.Authenticated(Secured.class)
	public static Result deleteMembershipRole(Long id, Long rid) {
		Membership m = Membership.read(id);
		if (m != null) {
			// TODO move all the role checking to another common place
			// TODO move all the role checking to another common place
			User requestor = User.findByAuthUserIdentity(PlayAuthenticate
					.getUser(session()));
			Boolean authorization = false;
			// what's the requestor membership in the group/assembly related to
			// this membership
			Membership requestorMembership = MembershipsDelegate.requestorMembership(requestor, m);

			if (requestorMembership != null) {
				authorization = MembershipsDelegate.requestorIsCoordinator(requestor,
						requestorMembership);
			} else {
				return unauthorized(Json.toJson(new TransferResponseStatus(
						ResponseStatus.UNAUTHORIZED,
						"Requestor is not member of this "
								+ m.getMembershipType())));
			}

			if (authorization) {
				
				if(m.getRoles().size()>1) {
					SecurityRole membershipRole = SecurityRole.read(rid);
					m.getRoles().remove(membershipRole);
					m.update();
					m.refresh();
					return ok(Json.toJson(m));
				} else {
					// leave always at least one Role				
					return badRequest(Json.toJson(new TransferResponseStatus(
					ResponseStatus.BADREQUEST, "Memberships must have at least one role")));
				}
			} else {
				return unauthorized(Json.toJson(new TransferResponseStatus(
						ResponseStatus.UNAUTHORIZED, "Requestor is not"
								+ MyRoles.COORDINATOR)));
			}
		} else {
			TransferResponseStatus responseBody = new TransferResponseStatus();
			responseBody.setStatusMessage("There is no membership with ID = "
					+ id);
			return notFound(Json.toJson(responseBody));
		}
	}

	// PUT /api/membership/:id controllers.Memberships.update(id: Long)
	@Security.Authenticated(Secured.class)
	public static Result updateMembershipStatus(Long id, String status) {
		String upStatus = status.toUpperCase();
		Membership m = Membership.read(id);
		// TODO move all the role checking to another common place
		User requestor = User.findByAuthUserIdentity(PlayAuthenticate
				.getUser(session()));
		Boolean authorization = false;
		// what's the requestor membership in the group/assembly related to
		// this membership
		Membership requestorMembership = MembershipsDelegate.requestorMembership(requestor, m);

		if (requestorMembership != null) {
			authorization = MembershipsDelegate.requestorIsCoordinator(requestor,
					requestorMembership);
		} else {
			return unauthorized(Json
					.toJson(new TransferResponseStatus(
							ResponseStatus.UNAUTHORIZED,
							"Requestor is not member of this "
									+ m.getMembershipType())));
		}

		if (authorization) {
			m.setStatus(MembershipStatus.valueOf(upStatus));
			m.update();
			m.refresh();
			return ok(Json.toJson(m));
		} else {
			return unauthorized(Json.toJson(new TransferResponseStatus(
					ResponseStatus.UNAUTHORIZED, "Requestor is not"
							+ MyRoles.COORDINATOR)));
		}
	}

	// DELETE /api/membership/:id controllers.Memberships.delete(id: Long)
	@Security.Authenticated(Secured.class)
	public static Result deleteMembership(Long id) {
		Membership m = Membership.read(id);
		User requestor = User.findByAuthUserIdentity(PlayAuthenticate
				.getUser(session()));

		// Any user can delete their own memberships
		if (MembershipsDelegate.isMembershipOfRequestor(requestor, m)) {
			m.delete();
			return ok(Json.toJson(new TransferResponseStatus(ResponseStatus.OK,
					"Membership was deleted")));
		} else {
			// Also COORDINATORS of the associated assembly/group can delete
			// memberships
			Boolean authorization = false;

			// what's the requestor membership in the group/assembly related to
			// this membership
			Membership requestorMembership = MembershipsDelegate.requestorMembership(requestor, m);

			if (requestorMembership != null) {
				authorization = MembershipsDelegate.requestorIsCoordinator(requestor,
						requestorMembership);
			} else {
				return unauthorized(Json.toJson(new TransferResponseStatus(
						ResponseStatus.UNAUTHORIZED,
						"Requestor is not member of this "
								+ m.getMembershipType())));
			}

			if (authorization) {
				m.delete();
				return ok(Json.toJson(new TransferResponseStatus(
						ResponseStatus.OK, "Membership was deleted")));
			} else {
				return unauthorized(Json.toJson(new TransferResponseStatus(
						ResponseStatus.UNAUTHORIZED, "Requestor is not"
								+ MyRoles.COORDINATOR)));
			}

		}
	}

	// TODO: TEST
	// GET /api/membership/verify/:token
	// controllers.Memberships.verifyMembership(token: String)
	public static Result verifyMembership(Long id, String token) {
		com.feth.play.module.pa.controllers.Authenticate.noCache(response());
		final TokenAction ta = Users.tokenIsValid(token, Type.MEMBERSHIP_INVITATION);
		if (ta == null) {
			return badRequest(Json.toJson(Messages.get("playauthenticate.token.error.message")));
			// TODO content negotiation: if content-type is HTML, render the response in HTML
			// return badRequest(no_token_or_invalid.render());
		}
		
		final String email = ta.targetUser.getEmail();
		Membership.verify(id, ta.targetUser);
		return ok(Json.toJson(Messages.get("playauthenticate.verify_email.success", email)));		
	}
	
	@ApiOperation(httpMethod = "GET", response = Membership.class, responseContainer = "List", produces = "application/json", value = "Update user information", notes = "Updates user information")
	@ApiResponses(value = { @ApiResponse(code = 404, message = "User or Memberships not found", response=TransferResponseStatus.class) })
	@ApiImplicitParams({
		@ApiImplicitParam(name="uuid", value="User's UUID", dataType="java.util.UUID", paramType="path"),
		@ApiImplicitParam(name="SESSION_KEY", value="User's session authentication key", dataType="String", paramType="header"),
		@ApiImplicitParam(name="type", value="type of membership requeste", dataType="String", paramType = "query", allowableValues ="assembly,group")
	})
	@Dynamic(value = "OnlyMeAndAdmin", meta = SecurityModelConstants.USER_RESOURCE_PATH)
	public static Result findMembershipByUser(UUID uuid, String type) {
		List<Membership> memberships = null;
		User u = User.findByUUID(uuid);
		if (u == null)
			return notFound(Json.toJson(new TransferResponseStatus(
					ResponseStatus.NOTAVAILABLE, "User with UUID = " + uuid
							+ " not found")));
		
		// TODO: include UUID already in the Membership model
		String membershipType = "";
		if(type!=null && !type.isEmpty()) {
			switch (type.toLowerCase()) {
			case "assembly":
				membershipType = "ASSEMBLY";
				break;
			case "group":
				membershipType = "GROUP";
				break;
			}
		} 
		memberships = Membership.findByUser(u,membershipType); 
		if (memberships == null || memberships.isEmpty())
			return notFound(Json.toJson(new TransferResponseStatus(
					ResponseStatus.NOTAVAILABLE, "No memberships for user with UUID = " + uuid)));
		else 
			return ok(Json.toJson(memberships));
	}
	
	@ApiOperation(httpMethod = "GET", response = TransferResponseStatus.class, responseContainer = "List", produces = "application/json", value = "Update user information", notes = "Updates user information")
	@ApiResponses(value = { @ApiResponse(code = 404, message = "User or Memberships not found", response=TransferResponseStatus.class) })
	@ApiImplicitParams({
		@ApiImplicitParam(name="targetUuid", value="Assembly/Group's target UUID", dataType="java.util.UUID", paramType="path"),
		@ApiImplicitParam(name="type", value="type of membership requeste", dataType="String", paramType = "query", allowableValues ="assembly,group"),
		@ApiImplicitParam(name="SESSION_KEY", value="User's session authentication key", dataType="String", paramType="header")
	})
	@Dynamic(value = "OnlyMeAndAdmin", meta = SecurityModelConstants.USER_RESOURCE_PATH)
	public static Result findMembershipByTargetUUID(UUID uuid, UUID targetUuid) {
		List<Membership> memberships = null;
		User u = User.findByUUID(uuid);
		if (u == null)
			return notFound(Json.toJson(new TransferResponseStatus(
					ResponseStatus.NOTAVAILABLE, "No user in session")));
				
		memberships = Membership.findByUserAndTargetUuid(u,targetUuid);
		if (memberships == null || memberships.isEmpty())
			return notFound(Json.toJson(new TransferResponseStatus(
					ResponseStatus.NOTAVAILABLE, "No memberships for requesting user in target assembly/group:" + targetUuid)));
		else 
			return ok(Json.toJson(new TransferResponseStatus(
					ResponseStatus.OK, "User is Member")));
		
	}
	
	/****************************************************************************************************************
	 * Not exposed methods
	 ****************************************************************************************************************/
	/**
	 * General create membership method (not exposed in the API)
	 * 
	 * @param membershipType
	 * @return
	 */
	protected static Result createMembership(User requestor,
			String targetCollection, Long targetCollectionId, String membershipType,
			Long userId, String userEmail, Long defaultRoleId, String defaultRoleName) {

		Pair<Membership, TransferResponseStatus> result = MembershipsDelegate
				.createMembership(requestor, targetCollection,
						targetCollectionId, membershipType, userId, userEmail,
						defaultRoleId, defaultRoleName);
		Membership m = result.getFirst();
		TransferResponseStatus r = result.getSecond();
		if(r==null) {
			return ok(Json.toJson(m));
		} else {
			if(r.getResponseStatus().equals(ResponseStatus.BADREQUEST)) return badRequest(Json.toJson(r));
			else if (r.getResponseStatus().equals(ResponseStatus.NODATA)) return notFound(Json.toJson(r));
			else if (r.getResponseStatus().equals(ResponseStatus.NOTAVAILABLE)) return notFound(Json.toJson(r));
			else if (r.getResponseStatus().equals(ResponseStatus.SERVERERROR)) return internalServerError(Json.toJson(r));
			else return unauthorized(Json.toJson(r));
		}
	}
}
