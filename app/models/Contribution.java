package models;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

import com.avaje.ebean.ExpressionList;
import enums.ContributionTypes;
import models.Location.Geo;

@Entity
public class Contribution extends AppCivistBaseModel {

	@Id
	@GeneratedValue
	private Long contributionId;
	private String title;
	private String text;
	private ContributionTypes type = ContributionTypes.COMMENT;
	private User author;

	@ManyToMany(cascade = CascadeType.ALL)
	private List<User> additionalAuthors = new ArrayList<User>();
	
	@ManyToOne
	private Assembly assembly;

	@ManyToMany(cascade = CascadeType.ALL)
	private List<Category> contributionCategories = new ArrayList<Category>();

	@OneToMany(cascade = CascadeType.ALL)
	private List<Resource> attachments = new ArrayList<Resource>();

	@OneToOne
	private Geo location;
	
	@ManyToMany(cascade = CascadeType.ALL)
	private List<Hashtag> hashtags = new ArrayList<Hashtag>();
	
	@OneToOne(cascade = CascadeType.ALL)
	private ContributionStatistics stats;
	
	// TODO think of how to connect and move through to the campaign phases
	
	/**
	 * The find property is an static property that facilitates database query
	 * creation
	 */
	public static Finder<Long, Contribution> find = new Finder<Long, Contribution>(
			Long.class, Contribution.class);

	public Contribution(User creator, String title, String text,
			ContributionTypes type) {
		super();
		this.author = creator;
		this.title = title;
		this.text = text;
		this.type = type;
	}

	public Contribution(User creator, String title, String brief,
			ContributionTypes type, Assembly assembly) {
		this.author = creator;
		this.title = title;
		this.text = brief;
		this.type = type;
		this.assembly = assembly;
	}

	public Contribution() {
		super();
	}

	/*
	 * Getters and Setters
	 */
	
	public Long getContributionId() {
		return contributionId;
	}

	public void setContributionId(Long contributionId) {
		this.contributionId = contributionId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public ContributionTypes getType() {
		return type;
	}

	public void setType(ContributionTypes type) {
		this.type = type;
	}

	public User getAuthor() {
		return author;
	}

	public void setAuthor(User creator) {
		this.author = creator;
	}

	public List<User> getAdditionalAuthors() {
		return additionalAuthors;
	}

	public void setAdditionalAuthors(List<User> additionalAuthors) {
		this.additionalAuthors = additionalAuthors;
	}

	public Assembly getAssembly() {
		return assembly;
	}

	public void setAssembly(Assembly assembly) {
		this.assembly = assembly;
	}
	
	public List<Category> getContributionCategories() {
		return contributionCategories;
	}

	public void setContributionCategories(List<Category> contributionCategories) {
		this.contributionCategories = contributionCategories;
	}

	public List<Resource> getAttachments() {
		return attachments;
	}

	public void setAttachments(List<Resource> attachments) {
		this.attachments = attachments;
	}

	public Geo getLocation() {
		return location;
	}

	public void setLocation(Geo location) {
		this.location = location;
	}

	public List<Hashtag> getHashtags() {
		return hashtags;
	}

	public void setHashtags(List<Hashtag> hashtags) {
		this.hashtags = hashtags;
	}

	public ContributionStatistics getStats() {
		return stats;
	}

	public void setStats(ContributionStatistics stats) {
		this.stats = stats;
	}

	/*
	 * Basic Data Operations
	 */
	public static Contribution create(User creator, String title, String text,
			ContributionTypes type) {
		Contribution c = new Contribution(creator, title, text, type);
		c.save();
		return c;
	}

	public static List<Contribution> findAll() {
		List<Contribution> contribs = find.all();
		return contribs;
	}


	public static List<Contribution> findAllByAssembly(Long aid) {
		List<Contribution> contribs = find.where().eq("assembly.assemblyId", aid).findList();
		return contribs;
	}
	public static void create(Contribution issue) {
		issue.save();
		issue.refresh();
	}

	public static Contribution read(Long issueId) {
		return find.ref(issueId);
	}

	public static Integer readByTitle(String title) {
		ExpressionList<Contribution> contributions = find.where().eq("title", title);
		return contributions.findList().size();
	}

	public static Contribution createObject(Contribution issue) {
		issue.save();
		return issue;
	}

	public static void delete(Long id) {
		find.ref(id).delete();
	}

	public static void delete(Contribution c) {
		c.delete();
	}

	public static Contribution update(Contribution c) {
		c.update();
		c.refresh();
		return c;
	}

	/*
	 * Other Queries
	 * DEPRECATED
	 */
	public static Contribution readIssueOfAssembly(Long assemblyId, Long contributionId) {
		return find.where().eq("assembly_assembly_id", assemblyId)
				.eq("contributionId", contributionId)
				.eq("type",ContributionTypes.ISSUE).findUnique();
	}
	
	public static List<Contribution> readContributionsOfAssembly(Long assemblyId) {
		return find.where().eq("assembly_assembly_id", assemblyId).findList();
	}
	
	public static List<Contribution> readIssuesOfAssembly(Long assemblyId) {
		return find.where().eq("assembly_assembly_id", assemblyId)
				.eq("type",ContributionTypes.ISSUE).findList();
	}
	
	public static List<Contribution> readIdeasOfAssembly(Long assemblyId) {
		return find.where().eq("assembly_assembly_id", assemblyId)
				.eq("type",ContributionTypes.IDEA).findList();
	}
	
	public static List<Contribution> readQuestionsOfAssembly(Long assemblyId) {
		return find.where().eq("assembly_assembly_id", assemblyId)
				.eq("type",ContributionTypes.QUESTION).findList();
	}
	
	public static List<Contribution> readCommentsOfAssembly(Long assemblyId) {
		return find.where().eq("assembly_assembly_id", assemblyId)
				.eq("type",ContributionTypes.COMMENT).findList();
	}
}