package controllers;

import static play.data.Form.form;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import models.Assembly;
import models.AssemblyProfile;
import models.Ballot;
import models.Campaign;
import models.Component;
import models.ComponentMilestone;
import models.Contribution;
import models.Membership;
import models.MembershipAssembly;
import models.NotificationEvent;
import models.ResourceSpace;
import models.User;
import models.WorkingGroup;
import models.transfer.AssemblyTransfer;
import models.transfer.NotificationSignalTransfer;
import models.transfer.TransferResponseStatus;
import models.transfer.UpdateTransfer;
import models.transfer.VotingBallotTransfer;
import be.objectify.deadbolt.java.actions.Dynamic;
import be.objectify.deadbolt.java.actions.SubjectPresent;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import enums.AppcivistNotificationTypes;
import enums.AppcivistResourceTypes;
import enums.NotificationEventName;
import enums.ResourceSpaceTypes;
import play.*;
import play.data.Form;
import play.i18n.Messages;
import play.libs.Json;
import play.libs.ws.WSResponse;
import play.mvc.*;
import security.SecurityModelConstants;
import utils.GlobalData;
import utils.services.NotificationServiceWrapper;
import views.html.*;
import http.Headers;

/**
 * Temporal controller to manage a mockup version of the notification server bus
 * TODO: replace this controller (or adapt) to use a notification queue like RabbitMQ
 * 
 * @author cdparra
 *
 */
@SuppressWarnings("unused")
@Api(value="06 notification: Notifications management")
@With(Headers.class)
public class Notifications extends Controller {

	final private static String NOTIFICATION_TITLE_ASSEMBLY_UPDATE = "notification.title.assembly.update";
	final private static String NOTIFICATION_TITLE_GROUP_UPDATE  = "notification.title.group.update";
	final private static String NOTIFICATION_TITLE_CONTRIBUTION_UPDATE  = "notification.title.contribution.update";
	final private static String NOTIFICATION_TITLE_CAMPAIGN_UPDATE  = "notification.title.campaign.update";
	final private static String NOTIFICATION_TITLE_MILESTONE_UPDATE  = "notification.title.campaign.update.milestone";
	final private static String NOTIFICATION_TITLE_MESSAGE_NEW = "notification.title.message.new";
	final private static String NOTIFICATION_TITLE_MESSAGE_REPLY = "notification.title.message.reply";
	final private static String NOTIFICATION_TITLE_MESSAGE_GROUP_NEW = "notification.title.message.new.group";
	final private static String NOTIFICATION_TITLE_MESSAGE_GROUP_REPLY = "notification.title.message.reply.group";
	final private static String NOTIFICATION_TITLE_MESSAGE_ASSEMBLY_NEW = "notification.title.message.new.assembly";
	final private static String NOTIFICATION_TITLE_MESSAGE_ASSEMBLY_REPLY = "notification.title.message.reply.assembly";
	
	final private static String NOTIFICATION_DESCRIPTION_ASSEMBLY_FORUM_CONTRIBUTION = "notification.description.assembly.forum.contribution";
	final private static String NOTIFICATION_DESCRIPTION_GROUP_FORUM_CONTRIBUTION = "notification.description.group.forum.contribution";
	final private static String NOTIFICATION_DESCRIPTION_CONTRIBUTION_COMMENT = "notification.description.contribution.comment";
	final private static String NOTIFICATION_DESCRIPTION_CAMPAIGN_CONTRIBUTION = "notification.description.campaign.contribution";
	final private static String NOTIFICATION_DESCRIPTION_UPCOMING_MILESTONE = "notification.description.campaign.upcoming.milestone";
	
	/**
	 * userInbox is the method called by the route GET /user/{uuid}/inbox
	 * it returns a list of TransferUpdate containing the latest news from User's assemblies, groups, and contributions
	 * @param userUUID
	 * @return
	 */
	@ApiOperation(httpMethod = "GET", response = UpdateTransfer.class, responseContainer="List", produces = "application/json", value = "Update user information", notes = "Updates user information")
	@ApiResponses(value = { @ApiResponse(code = 404, message = "User not found", response=TransferResponseStatus.class) })
	@ApiImplicitParams({
		//@ApiImplicitParam(name="user", value="user", dataType="String", defaultValue="user", paramType = "path"),
		@ApiImplicitParam(name="uuid", value="User's UUID", dataType="Long", paramType="path"),
		@ApiImplicitParam(name="SESSION_KEY", value="User's session authentication key", dataType="String", paramType="header")
	})
	@Dynamic(value = "OnlyMe", meta = SecurityModelConstants.USER_RESOURCE_PATH)
	public static Result userInbox(UUID userUUID) {
		// 0. Obtain User
		User u = User.findByUUID(userUUID);
		// 1. Get a list of Assemblies to which the User belongs
		List<Membership> myAssemblyMemberships = Membership.findByUser(u, "ASSEMBLY");
		// 2. Get a list of Working Groups to which the User belongs
		List<Membership> myGroupMemberships = Membership.findByUser(u, "GROUP");
		// 3. Get a list of Contributions by the user 
		List<Contribution> myContribs = Contribution.readByCreator(u);
		
		List<UpdateTransfer> updates = new ArrayList<UpdateTransfer>();

		// 4. Process AssemblyMemberships to get
		// 4.1. New Assembly Forum Posts
		// 4.2. Current Ongoing Campaigns Upcoming Milestones
		// 4.3. Current Ongoing Campaigns Latest Contribution
		updates = processMyAssemblies(u, updates, myAssemblyMemberships);
		
		// 5. Process GroupMemberships to get
		// 5.1. New Group Forum Posts
		// 5.2. New comments related to Group Contributions
		//TODO: updates = processMyGroups(u, updates, myGroupMemberships);
		
		// 6. Process Contributions to get comments on them
		//TODO: updates = processMyContributions(u, updates, myContribs);
		if (!updates.isEmpty()) return ok(Json.toJson(updates));
		else
			return notFound(Json.toJson(new TransferResponseStatus("No updates")));
	}

	/* PRIVATE METHODS */
	private static List<UpdateTransfer> processMyAssemblies(User u,
			List<UpdateTransfer> updates, List<Membership> myAssemblyMemberships) {

		for (Membership membership : myAssemblyMemberships) {
			Assembly a = ((MembershipAssembly) membership).getAssembly();
			
			// 4.1. New Assembly Forum Posts
			ResourceSpace aForum = a.getForum();
			List<Contribution> posts = null;
			Contribution latestForumPost = null;
			if (aForum !=null) posts = aForum.getContributions();
			if (posts != null && !posts.isEmpty()) latestForumPost = posts.get(posts.size()-1);
			if (latestForumPost != null) {
				UpdateTransfer up = UpdateTransfer.getInstance(
						AppcivistNotificationTypes.ASSEMBLY_UPDATE,
						AppcivistResourceTypes.CONTRIBUTION_COMMENT,
						AppcivistResourceTypes.ASSEMBLY,
						NOTIFICATION_TITLE_ASSEMBLY_UPDATE,
						NOTIFICATION_DESCRIPTION_ASSEMBLY_FORUM_CONTRIBUTION, 
						u.getName(), 
						u.getLanguage(), 
						a.getAssemblyId(),
						a.getUuid(), 
						a.getName(), 
						latestForumPost.getContributionId(),
						latestForumPost.getUuid(), 
						latestForumPost.getTitle(),
						latestForumPost.getText(), 
						latestForumPost.getAuthors().get(0).getName(), 
						latestForumPost.getCreation());
				up.setRelativeUrl("/assembly/"+a.getAssemblyId()+"/forum");
				up.setResourceProperty("author",latestForumPost.getFirstAuthorName());
				up.setResourceProperty("title",latestForumPost.getTitle());
				up.setResourceProperty("text",latestForumPost.getText());
				up.setResourceProperty("themes", latestForumPost.getThemes());
				up.setContainerProperty("title",a.getName());
				updates.add(up);
			}
			
			
			// 4.2. Current Ongoing Campaigns Upcoming Milestones
			ResourceSpace resources = a.getResources();
			List<Campaign> campaigns = null;
			if (resources !=null) campaigns = resources.getCampaigns();
			if (campaigns != null && !campaigns.isEmpty()) {
				for (Campaign c : campaigns) {
					List<Component> components = c.getResources().getComponents();
					if (components != null && !components.isEmpty()) {
						for (Component p : components) {
							Calendar today = Calendar.getInstance();
							if (p.getEndDate() !=null && p.getEndDate().after(today.getTime())) {
								// 4.2. Current Ongoing Campaigns Upcoming Milestones
								List<ComponentMilestone> milestones = p.getMilestones();
								if (milestones!=null && !milestones.isEmpty()) {
									for (ComponentMilestone m : milestones) {
										Date mStart = m.getDate();
										Calendar cal = Calendar.getInstance();
										cal.setTime(mStart); // Now use today date.
										cal.add(Calendar.DATE, m.getDays());
										SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
										if(cal.getTime().after(today.getTime())) {
											UpdateTransfer up = UpdateTransfer.getInstance(
													AppcivistNotificationTypes.UPCOMING_MILESTONE,
													AppcivistResourceTypes.CAMPAIGN_COMPONENT,
													AppcivistResourceTypes.CAMPAIGN,
													NOTIFICATION_TITLE_MILESTONE_UPDATE,
													NOTIFICATION_DESCRIPTION_UPCOMING_MILESTONE,
													u.getName(),
													u.getLanguage(),
													c.getCampaignId(),
													c.getUuid(),
													c.getTitle(),
													m.getComponentMilestoneId(),
													m.getUuid(), m.getTitle(),
													sdf.format(cal.getTime()),
													a.getName(), 
													m.getCreation());
											up.setRelativeUrl(
													"/assembly/"+a.getAssemblyId()
													+"/campaign/"+c.getCampaignId()
													+"/"+p.getComponentId()
													+"/"+m.getComponentMilestoneId());
											up.setContainerProperty("title",c.getTitle());
											updates.add(up);
										}
									}
								}
							}

							// TODO: 4.3. Current Ongoing Campaigns Latest Contribution
						}
					}
					
				}
			}
		}		
		return updates;
	}

	private static List<UpdateTransfer> processMyGroups(User u,
			List<UpdateTransfer> updates, List<Membership> myGroupMemberships) {
		for (Membership membership : myGroupMemberships) {
			WorkingGroup g = membership.getTargetGroup();
			
			// 4.1. New Group Forum Posts
			// TODO
//			ResourceSpace gForum = g.getResources().get;
//			List<Contribution> posts = null;
//			Contribution latestForumPost = null;
//			if (gForum !=null) posts = gForum.getContributions();
//			if (posts != null && !posts.isEmpty()) latestForumPost = posts.get(0);
//			if (latestForumPost != null)
//				updates.add(TransferUpdate.getInstance(
//						AppcivistNotificationTypes.ASSEMBLY_UPDATE,
//						AppcivistResourceTypes.CONTRIBUTION_COMMENT,
//						AppcivistResourceTypes.ASSEMBLY,
//						NOTIFICATION_TITLE_ASSEMBLY_UPDATE,
//						NOTIFICATION_DESCRIPTION_ASSEMBLY_FORUM_CONTRIBUTION, u
//								.getName(), u.getLanguage(), g.getAssemblyId(),
//						g.getUuid(), g.getName(), latestForumPost
//								.getContributionId(),
//						latestForumPost.getUuid(), latestForumPost.getTitle(),
//						latestForumPost.getText(), latestForumPost.getAuthor()
//								.getName(), latestForumPost.getCreation()));
		}
		
		return updates;
	}

	private static List<UpdateTransfer> processMyContributions(User u,
			List<UpdateTransfer> updates, List<Contribution> myContribs) {
		// TODO
		
		return null;
	}


}
