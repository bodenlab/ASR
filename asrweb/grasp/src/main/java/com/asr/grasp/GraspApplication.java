package com.asr.grasp;

import com.asr.grasp.controller.ReconstructionController;
import com.asr.grasp.controller.UserController;
import com.asr.grasp.objects.Reconstruction;
import com.asr.grasp.objects.User;
import com.asr.grasp.objects.Share;
import com.asr.grasp.validator.LoginValidator;
import com.asr.grasp.validator.UserValidator;
import com.asr.grasp.view.AccountView;
import json.JSONArray;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.SessionScope;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Value;
//import org.springframework.security.crypto.bcrypt.BCrypt;

@Controller
@SpringBootApplication
@SessionScope
public class GraspApplication extends SpringBootServletInitializer {

	@Value("${project.sessionPath}")
	private String sessionPath;

	private final static Logger logger = Logger.getLogger(GraspApplication.class.getName());

	private ASRThread recon = null;

	@Autowired
	private UserValidator userValidator;

	@Autowired
	private AccountView accountView;

	@Autowired
	private LoginValidator loginValidator;

	@Autowired
	private UserController userController;

	@Autowired
	private ReconstructionController reconController;

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(GraspApplication.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(GraspApplication.class, args);
	}

	private User loggedInUser = new User();

	private Reconstruction currRecon = new Reconstruction();

	@Autowired
	private ASR asr;

	/**
	 * Initialise the initial form in the index
	 *
	 * @return index html
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public ModelAndView showForm(Model model) {
		this.asr = new ASR();
		model.addAttribute("asrForm", this.asr);
		model.addAttribute("username", loggedInUser.getUsername());
		return new ModelAndView("index");
	}

	@RequestMapping(value = "/register", method = RequestMethod.GET)
	public ModelAndView showRegistrationForm(WebRequest request, Model model) {
		model.addAttribute("user", loggedInUser);
		return new ModelAndView("register");
	}

	@RequestMapping(value = "/login", method = RequestMethod.GET)
	public ModelAndView showLoginForm(WebRequest request, Model model) {
		model.addAttribute("user", loggedInUser);
		model.addAttribute("username", null);
		return new ModelAndView("login");
	}

	@RequestMapping(value = "/account", method = RequestMethod.GET)
	public ModelAndView showAccount(WebRequest request, Model model) {
		reconController.checkObsolete();
		return accountView.get(loggedInUser, userController);
	}

	@RequestMapping(value = "/", method = RequestMethod.GET, params = {"cancel"})
	public ModelAndView cancelRecon(WebRequest request, Model model) {
		if (recon != null)
			recon.interrupt();

		if (asr.performedRecon())
			return returnASR(model);

		return showForm(model);

	}

	/**
	 * ToDo need to change to int reconId from long id.
	 *  Deletes a reconstruction
	 *
	 * @param delete
	 * @param reconId
	 * @param webrequest
	 * @param model
	 * @return the view for the account page.
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET, params =
			{"delete", "id"})
	public ModelAndView deleteRecon(@RequestParam("delete") String delete,
									@RequestParam("id") int reconId, WebRequest
												webrequest, Model model) {

		ModelAndView mav = accountView.get(loggedInUser, userController);
		// Need to check if the users details were correct
		String err = reconController.delete(reconId, loggedInUser);

		if (err != null) {
			mav.addObject("warning", err);
		} else {
			mav.addObject("type", "deleted");
			mav.addObject("warning", null);
		}

		return mav;
	}

	/**
	 * Shares the reconsrtruction with another user by their username.
	 *
	 * ToDo: Need to look at what the shareObject was
	 * @param share
	 * @param shareObject
	 * @param bindingResult
	 * @param model
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "/", method = RequestMethod.POST, params = {"share"})
	public ModelAndView shareRecon(@RequestParam("share") String share,
								   @ModelAttribute("share") Share shareObject,
								   BindingResult bindingResult, Model model, HttpServletRequest request) {
		ModelAndView mav = accountView.get(loggedInUser, userController);
		// Share it with the user
		String err = reconController.shareWithUser(shareObject.getReconID(),
				shareObject.getUsername(), loggedInUser);

		if (err != null) {
			mav.addObject("warning", err);
		} else {
			mav.addObject("type", "shared");
			mav.addObject("warning", null);
		}

		return mav;
	}

	/**
	 * Loads a reconstruction based on the ID. ID is the reconstruction ID.
	 * @param load
	 * @param id
	 * @param webrequest
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET, params = {"load", "id"})
	public ModelAndView loadRecon(@RequestParam("load") String load,
								  @RequestParam("id") int id, WebRequest
											  webrequest, Model model) {

		// Here since we store the current reconsruction we just need to
		// update the reconstruction that it is pointing at.

		Reconstruction recon = reconController.getById(id,
				loggedInUser);
		// We want to return that the reconstruction doesn't exist if it
		// isn't in the db or the user doesn't have access
		if (recon == null) {
			return showError(model);
		}

		// Otherwise we want to set this for the user.
		userController.setCurrRecon(recon, loggedInUser);

		currRecon = loggedInUser.getCurrRecon();

		asr = new ASR();
		asr.setLabel(currRecon.getLabel());
		asr.setInferenceType(currRecon.getInferenceType());
		asr.setModel(currRecon.getModel());
		asr.setNodeLabel(currRecon.getNode());
		asr.setTree(currRecon.getTree());
		asr.setReconstructedTree(currRecon.getReconTree());
		asr.setMSA(currRecon.getMsa());
		asr.setAncestor(currRecon.getAncestor());
		asr.loadSequences(currRecon.getSequences());
		asr.setJointInferences(currRecon.getJointInferences());
		asr.loadParameters();

		ModelAndView mav = new ModelAndView("index");

		mav.addObject("label", asr.getLabel());

		// add reconstructed newick string to send to javascript
		mav.addObject("tree", asr.getReconstructedNewickString());

		// add msa and inferred ancestral graph
		String graphs = asr.catGraphJSONBuilder(asr.getMSAGraph(), asr.getAncestorGraph());
		mav.addObject("graph", graphs);

		// add attribute to specify to view results (i.e. to show the graph, tree, etc)
		mav.addObject("inferenceType", asr.getInferenceType());
		mav.addObject("node", asr.getNodeLabel());
		mav.addObject("results", true);

		mav.addObject("user", loggedInUser);
		mav.addObject("username", loggedInUser.getUsername());
		return mav;
	}

	/**
	 * Logs in the user and takes them to their account page.
	 *
	 * @param user
	 * @param bindingResult
	 * @param model
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "/login", method = RequestMethod.POST)
	public ModelAndView loginUser(@Valid @ModelAttribute("user") User user, BindingResult bindingResult, Model model, HttpServletRequest request) {

		loginValidator.validate(user, bindingResult);

		if (bindingResult.hasErrors())
			return new ModelAndView("login");

		// If we have passed the validation this means that the username and
		// password are correct.
		String err = userController.loginUser(user);

		userController.getId(user);

		loggedInUser = user;

		reconController.checkObsolete();

		ModelAndView mav = accountView.get(loggedInUser, userController);

		// CHeck that err wasn't try
		if (err != null) {
			mav.addObject("warning", err);
		} else {
			mav.addObject("type", "shared");
			mav.addObject("warning", null);
		}

//		if (currentRecon != null) {
//			//if (registered.getNonSharedReconstructions().size() == MAX_RECONS) {
//				mav.addObject("warning", reconstructionService.getLiveTime());
//			//	mav.addObject("type", null);
//			//} else {
//				registered = reconstructionService.saveNewReconstruction(currentRecon, registered);
//				mav.addObject("type", "saved");
//				currentRecon = null;
//			//}
//		}
;
		return mav;
	}

	/**
	 * Registers a new user account and sends the user to the accounts page.
	 * @param user
	 * @param bindingResult
	 * @param model
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "/register", method = RequestMethod.POST)
	public ModelAndView registerUser(@Valid @ModelAttribute("user") User user, BindingResult bindingResult, Model model, HttpServletRequest request) {

		userValidator.validate(user, bindingResult);

		if (bindingResult.hasErrors()) {
			return new ModelAndView("register");
		}

		String err = userController.register(user);

		if (err != null) {
			// Probably should add an error here
			return new ModelAndView("register");
		}
		// Otherwise we want to get the now logged in user by ID
		userController.getId(user);

		// Set the loggedInUser
		loggedInUser = user;

		// Send them to their accounts page
		return accountView.get(loggedInUser, userController);

//		if (currentRecon != null)
//			registered = reconstructionService.saveNewReconstruction(currentRecon, registered);
//		currentRecon = null;

	}

//	/**
//	 * ToDo: Implememnt send registration email. This will enable users to
//	 * reset passwords etc.
//	 * @param registered
//	 * @return
//	 */
//	private User sendRegistrationEmail(User registered, HttpServletRequest request) {
//		// Disable user until they click on confirmation link in email
//		// registered.setEnabled(false);
//
//		// Generate random 36-character string token for confirmation link
//		registered.setConfirmationToken(UUID.randomUUID().toString());
//
//		String appUrl = request.getScheme() + "://" + request.getServerName();
//
//		SimpleMailMessage registrationEmail = new SimpleMailMessage();
//		registrationEmail.setTo(registered.getEmail());
//		registrationEmail.setSubject("Registration Confirmation");
//		registrationEmail.setText("To confirm your e-mail address, please click the link below:\n"
//				+ appUrl + "/confirm?token=" + registered.getConfirmationToken());
//		registrationEmail.setFrom("noreply@domain.com");
//
//		emailService.sendEmail(registrationEmail);
//		return registered;
//	}
//
//	private com.asr.grasp.User createUserAccount(com.asr.grasp.User user){
//		return service.registerNewUserAccount(user);
//	}
//
//	private com.asr.grasp.User getUserAccount(com.asr.grasp.User user){
//		return service.getUserAccount(user);
//	}

	/**
	 * Show guide
	 *
	 * @return guide html
	 */
	@RequestMapping(value = "/guide", method = RequestMethod.GET)
	public ModelAndView showGuide(Model model) {
		ModelAndView mav = new ModelAndView("guide");
		mav.addObject("results", asr.getLabel() != "");
		mav.addObject("user", loggedInUser);
		mav.addObject("username", loggedInUser.getUsername());
		return mav;
	}

	/**
	 * Show workshop tutorial
	 *
	 * @return guide html
	 */
	@RequestMapping(value = "/tutorial", method = RequestMethod.GET)
	public ModelAndView showTutorial(Model model) {
		ModelAndView mav = new ModelAndView("tutorial");
		mav.addObject("results", asr.getLabel() != "");
		mav.addObject("user", loggedInUser);
		mav.addObject("username", loggedInUser.getUsername());
		return mav;
	}

	/**
	 * Show max likelihood info
	 *
	 * @return guide html
	 */
	@RequestMapping(value = "/ml", method = RequestMethod.GET)
	public ModelAndView showMlInfo(Model model) {
		ModelAndView mav = new ModelAndView("ml");
		mav.addObject("results", asr.getLabel() != "");
		mav.addObject("user", loggedInUser);
		mav.addObject("username", loggedInUser.getUsername());
		return mav;
	}

	/**
	 * Save reconstruction
	 *
	 * @return account html
	 */
	@RequestMapping(value = "/save", method = RequestMethod.GET)
	public ModelAndView saveRecon(WebRequest request, Model model) throws IOException {
		// Saves the current reconstruction

		// if a user is not logged in, prompt to login
		if (loggedInUser.getUsername() == null || loggedInUser.getUsername() == "") {
			ModelAndView mav = new ModelAndView("login");
			mav.addObject("user", loggedInUser);
			return mav;
		}

		String err = reconController.save(loggedInUser, currRecon);

		// Check if we were able to save it
		if (err != null) {
			// ToDo: Something
			return showError(model);
		}

		return accountView.get(loggedInUser, userController);
	}

	/**
	 * Show error
	 *
	 * @return index html
	 */
	@RequestMapping(value = "/error", method = RequestMethod.GET)
	public ModelAndView showError(Model model) {
		ModelAndView mav = new ModelAndView("index");
		mav.addObject("asrForm", asr);
		mav.addObject("error", true);
		mav.addObject("errorMessage", "Sorry! An unknown error occurred. Please check the error types in the guide and retry your reconstruction... ");
		mav.addObject("username",  loggedInUser.getUsername());
		return mav;
	}

	/**
	 * Show status of reconstruction while asynchronously performing analysis
	 *
	 * @return status of reconstruction
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET, params = {"request"})
	public @ResponseBody String showStatus(@RequestParam("request") String request, Model model) throws Exception {

		String status = recon.getStatus();

		if (status != null && (status.equalsIgnoreCase("done") || status.contains("error"))) {
			String stat = status;
			asr.setFirstPass(true); // reset flag
			asr.setPrevProgress(0);
			return stat;
		}

		// try to get current node ID
		int progress = asr.getNumberAlnCols() == 0 ? 0 : (100*asr.getReconCurrentNodeId())/asr.getNumberAlnCols();
		if (asr.getFirstPass() && progress < asr.getPrevProgress())
			asr.setFirstPass(false);

		progress = asr.getFirstPass() ? progress/2 : 50 + progress/2;
		if (progress > asr.getPrevProgress())
			asr.setPrevProgress(progress);

		return asr.getPrevProgress() + "%";
	}

	@RequestMapping(value = "/", method = RequestMethod.GET, params={"getrecon"})
	public ModelAndView returnASR(Model model) {

		ModelAndView mav = new ModelAndView("index");

		// Here we need to update the reconstruction to have all the
		// parameters of the ASR.
		currRecon = reconController.createFromASR(asr);
		userController.setCurrRecon(currRecon, loggedInUser);

		mav.addObject("label", asr.getLabel());

		// add reconstructed newick string to send to javascript
		mav.addObject("tree", asr.getReconstructedNewickString());

		// add msa and inferred ancestral graph
		String graphs = asr.catGraphJSONBuilder(asr.getMSAGraphJSON(), asr.getAncestralGraphJSON(asr.getWorkingNodeLabel()));
		mav.addObject("graph", graphs);

		// add attribute to specify to view results (i.e. to show the graph, tree, etc)
		mav.addObject("inferenceType", asr.getInferenceType());
		mav.addObject("results", true);
		mav.addObject("node", asr.getNodeLabel());
		mav.addObject("username",  loggedInUser.getUsername());

		return mav;
	}

	@RequestMapping(value = "/", method = RequestMethod.POST, params={"getrecongraph"})
	public @ResponseBody String returnASRGraph(@RequestParam("getrecongraph") String getrecongraph, Model model, HttpServletRequest request) {

		model.addAttribute("label", asr.getLabel());

		// add reconstructed newick string to send to javascript
		model.addAttribute("tree", asr.getReconstructedNewickString());

		// add msa and inferred ancestral graph
		String graphs = asr.catGraphJSONBuilder(asr.getMSAGraphJSON(), asr.getAncestralGraphJSON(asr.getWorkingNodeLabel()));
		model.addAttribute("graph", graphs);

		// add attribute to specify to view results (i.e. to show the graph, tree, etc)
		model.addAttribute("inferenceType", asr.getInferenceType());
		model.addAttribute("results", true);
		model.addAttribute("node", asr.getNodeLabel());
		model.addAttribute("username",  loggedInUser.getUsername());

		return graphs;
	}


	/**
	 * Submit the asr form (documenting input details, i.e. aln and tree file, etc)
	 *
	 * @param asrForm       ASR object
	 * @param bindingResult Form result, indicating any input errors
	 * @param model         com model
	 * @return index with results as attributes in the model
	 */
	@RequestMapping(value = "/", method = RequestMethod.POST, params = "submitAsr")
	public ModelAndView performReconstruction(@Valid @ModelAttribute("asrForm") ASR asrForm, BindingResult bindingResult, Model model, HttpServletRequest request) throws Exception {

		this.asr = asrForm;

		logger.log(Level.INFO, "NEW, request_addr: " + request.getRemoteAddr() + ", infer_type: " + asr.getInferenceType());// + ", mem_bytes: " + ObjectSizeCalculator.getObjectSize(asr));


		// upload supplied files
		try {
			File sessionDir = new File(sessionPath + asr.getSessionId());
			if (!sessionDir.exists())
				sessionDir.mkdir();

			asr.setSessionDir(sessionDir.getAbsolutePath() + "/");

			if (asr.getSeqFile() != null || asr.getAlnFile() != null) {
				// aligning input data before performing reconstruction
				if (asr.getSeqFile() != null) {
					asr.getSeqFile().transferTo(new File(asr.getSessionDir() + asr.getSeqFile().getOriginalFilename()));
					asr.setAlnFilepath(asr.getSessionDir() + asr.getSeqFile().getOriginalFilename());
					asr.setPerformAlignment(true);
				}
				// performing reconstruction on already aligned data
				if (asr.getAlnFile() != null) {
					asr.getAlnFile().transferTo(new File(asr.getSessionDir() + asr.getAlnFile().getOriginalFilename()));
					asr.setAlnFilepath(asr.getSessionDir() + asr.getAlnFile().getOriginalFilename());
				}
				asr.getTreeFile().transferTo(new File(asr.getSessionDir() + asr.getTreeFile().getOriginalFilename()));
				asr.setTreeFilepath(asr.getSessionDir() + asr.getTreeFile().getOriginalFilename());
			} else {
				// performing reconstruction on test data
				File alnFile = new File(Thread.currentThread().getContextClassLoader().getResource(asr.getData() + ".aln").toURI());
				asr.setAlnFilepath(asr.getSessionDir() + asr.getData() + ".aln");
				Files.copy(alnFile.toPath(), (new File(asr.getAlnFilepath())).toPath(), StandardCopyOption.REPLACE_EXISTING);
				File treeFile = new File(Thread.currentThread().getContextClassLoader().getResource(asr.getData() + ".nwk").toURI());
				asr.setTreeFilepath(asr.getSessionDir() + asr.getData() + ".nwk");
				Files.copy(treeFile.toPath(), (new File(asr.getTreeFilepath())).toPath(), StandardCopyOption.REPLACE_EXISTING);
			}

			if (asr.getLabel() == "")
				asr.setLabel("Grasp");

		} catch (Exception e) {
			ModelAndView mav = new ModelAndView("index");
			mav.addObject("error", true);
			String message = e.getMessage();
			logger.log(Level.SEVERE, "ERR, request_addr: " + request.getRemoteAddr() + " error: " + message);
			if (e.getMessage() == null || e.getMessage().contains("FileNotFoundException"))
				message = checkErrors(asr);
			mav.addObject("errorMessage", message);
			mav.addObject("user", loggedInUser);
			mav.addObject("username", loggedInUser.getUsername());
			System.err.println("Error: " + message);
			return mav;
		}

		// run reconstruction
		recon = new ASRThread(asr, asr.getInferenceType(), asr.getNodeLabel(), false, logger);

		ModelAndView mav = new ModelAndView("processing");
		mav.addObject("user", loggedInUser);
		mav.addObject("username", loggedInUser.getUsername());
		return mav;
	}

	/**
	 * Perform marginal reconstruction of specified tree node.
	 *
	 * @param infer inference type (Expects marginal)
	 * @param node  node label
	 * @param model com model
	 * @return graphs in JSON format
	 */
	@RequestMapping(value = "/", method = RequestMethod.POST, params = {"infer", "node", "addgraph"})
	public ModelAndView performReconstruction(@RequestParam("infer") String infer, @RequestParam("node") String node, @RequestParam("addgraph") Boolean addGraph, Model model, HttpServletRequest request) {

		ModelAndView mav = new ModelAndView("processing");

		// run reconstruction
		recon = new ASRThread(asr, infer, node, addGraph, logger);

		mav.addObject("username",  loggedInUser.getUsername());
		return mav;
	}

	/**
	 * Download files from reconstruction
	 *
	 * @param request   HTTP request (form request specifying parameters)
	 * @param response  HTTP response to send data to client
	 * @throws IOException
	 */
	@RequestMapping(value = "/download-tutorial-files", method = RequestMethod.GET, produces = "application/zip")
	public void downloadTutorial(HttpServletRequest request, HttpServletResponse response) throws IOException, URISyntaxException {
		response.setStatus(HttpServletResponse.SC_OK);
		response.setHeader("Content-Disposition", "attachment; filename=\"GRASP_Tutorial.zip\"");

		// create temporary folder to send output as zipped files
		if (asr.getSessionDir() == null) {
			File sessionDir = new File(sessionPath + asr.getSessionId());
			if (!sessionDir.exists())
				sessionDir.mkdir();
			asr.setSessionDir(sessionDir.getAbsolutePath() + "/");
		}

		String tempDir = asr.getSessionDir() + "/GRASP_Tutorial";
		File sessionDir = new File(tempDir);
		if (sessionDir.exists()) {
			for (File file : sessionDir.listFiles())
				file.delete();
			sessionDir.delete();
		}
		sessionDir.mkdir();


		// copy output files to temporary folder, or generate output where needed and save in temporary folder
		File tutorialFile = new File(Thread.currentThread().getContextClassLoader().getResource("GRASPTutorial.fasta").toURI());
		Files.copy(tutorialFile.toPath(), (new File(tempDir + "/GRASPTutorial.fasta")).toPath(), StandardCopyOption.REPLACE_EXISTING);

		// send output folder to client
		ZipOutputStream zout = new ZipOutputStream(response.getOutputStream());
		zipFiles(sessionDir, zout);
		zout.close();
	}

	/**
	 * Download files from reconstruction
	 *
	 * @param request   HTTP request (form request specifying parameters)
	 * @param response  HTTP response to send data to client
	 * @throws IOException
	 */
	@RequestMapping(value = "/download-tutorial-files-aln", method = RequestMethod.GET, produces = "application/zip")
	public void downloadTutorialAln(HttpServletRequest request, HttpServletResponse response) throws IOException, URISyntaxException {
		response.setStatus(HttpServletResponse.SC_OK);
		response.setHeader("Content-Disposition", "attachment; filename=\"GRASP_Tutorial.zip\"");

		// create temporary folder to send output as zipped files
		if (asr.getSessionDir() == null) {
			File sessionDir = new File(sessionPath + asr.getSessionId());
			if (!sessionDir.exists())
				sessionDir.mkdir();
			asr.setSessionDir(sessionDir.getAbsolutePath() + "/");
		}

		String tempDir = asr.getSessionDir() + "/GRASP_Tutorial";
		File sessionDir = new File(tempDir);
		if (sessionDir.exists()) {
			for (File file : sessionDir.listFiles())
				file.delete();
			sessionDir.delete();
		}
		sessionDir.mkdir();


		// copy output files to temporary folder, or generate output where needed and save in temporary folder
		File tutorialFile = new File(Thread.currentThread().getContextClassLoader().getResource("GRASPTutorial.aln").toURI());
		Files.copy(tutorialFile.toPath(), (new File(tempDir + "/GRASPTutorial.aln")).toPath(), StandardCopyOption.REPLACE_EXISTING);
		tutorialFile = new File(Thread.currentThread().getContextClassLoader().getResource("GRASPTutorial.nwk").toURI());
		Files.copy(tutorialFile.toPath(), (new File(tempDir + "/GRASPTutorial.nwk")).toPath(), StandardCopyOption.REPLACE_EXISTING);

		// send output folder to client
		ZipOutputStream zout = new ZipOutputStream(response.getOutputStream());
		zipFiles(sessionDir, zout);
		zout.close();
	}

	/**
	 * Download files from reconstruction
	 *
	 * @param request   HTTP request (form request specifying parameters)
	 * @param response  HTTP response to send data to client
	 * @throws IOException
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET, params = "download", produces = "application/zip")
	public void showForm(HttpServletRequest request, HttpServletResponse response) throws IOException {
		JSONArray graphs = new JSONArray(request.getParameter("graphs-input"));
		String[] ancs = new String[graphs.length()];
		for (int i = 0; i < graphs.length(); i++) {
			ancs[i] = graphs.getString(i);
			System.out.println(ancs[i]);
		}

		response.setStatus(HttpServletResponse.SC_OK);
		response.setHeader("Content-Disposition", "attachment; filename=\"GRASP_" + asr.getLabel() + ".zip\"");

		// create temporary folder to send output as zipped files
		if (asr.getSessionDir() == null) {
			File sessionDir = new File(sessionPath + asr.getSessionId());
			if (!sessionDir.exists())
				sessionDir.mkdir();
			asr.setSessionDir(sessionDir.getAbsolutePath() + "/");
		}

		String tempDir = asr.getSessionDir() + "/GRASP_" + asr.getLabel();
		File sessionDir = new File(tempDir);
		if (sessionDir.exists()) {
			for (File file : sessionDir.listFiles())
				file.delete();
			sessionDir.delete();
		}
		sessionDir.mkdir();


		// copy output files to temporary folder, or generate output where needed and save in temporary folder
		if (request.getParameter("check-recon-tree") != null && request.getParameter("check-recon-tree").equalsIgnoreCase("on")) {
			File nwkFile = new File(asr.getSessionDir() + asr.getReconstructedTreeFileName());
			if (nwkFile.exists())
				Files.copy((new File(asr.getSessionDir() + asr.getReconstructedTreeFileName())).toPath(),
					(new File(tempDir + "/" + asr.getReconstructedTreeFileName())).toPath(), StandardCopyOption.REPLACE_EXISTING);
			else
				asr.saveTree((new File(tempDir + "/" + asr.getReconstructedTreeFileName())).toPath().toString());
		}
		if (request.getParameter("check-pog-msa") != null && request.getParameter("check-pog-msa").equalsIgnoreCase("on"))
			asr.saveMSA(tempDir + "/");
		if (request.getParameter("check-pog-marg") != null && request.getParameter("check-pog-marg").equalsIgnoreCase("on"))
			asr.saveAncestorGraph(request.getParameter("node-label"), tempDir + "/", false);
		if (request.getParameter("check-marg-dist") != null && request.getParameter("check-marg-dist").equalsIgnoreCase("on"))
			asr.saveMarginalDistribution(tempDir, request.getParameter("joint-node"));
		if (request.getParameter("check-pog-joint") != null && request.getParameter("check-pog-joint").equalsIgnoreCase("on"))
			asr.saveAncestors(tempDir + "/", ancs);
		if (request.getParameter("check-pog-joint-single") != null && request.getParameter("check-pog-joint-single").equalsIgnoreCase("on"))
			asr.saveAncestorGraph(request.getParameter("joint-node"), tempDir + "/", true);
		if (request.getParameter("check-seq-marg") != null && request.getParameter("check-seq-marg").equalsIgnoreCase("on"))
			asr.saveConsensusMarginal(tempDir + "/" + request.getParameter("joint-node") + "_consensus");
		if (request.getParameter("check-seq-joint-single") != null && request.getParameter("check-seq-joint-single").equalsIgnoreCase("on"))
			asr.saveConsensusJoint(tempDir + "/" + request.getParameter("joint-node") + "_consensus", request.getParameter("joint-node"));
//		if (request.getParameter("check-msa-marg-dist") != null && request.getParameter("check-msa-marg-dist").equalsIgnoreCase("on"))
//			asr.saveMarginalDistribution(tempDir + "/", "msa");
		if (request.getParameter("check-seq-joint") != null && request.getParameter("check-seq-joint").equalsIgnoreCase("on"))
			asr.saveConsensusJoint(tempDir + "/ancestors_consensus", ancs);
//		if (request.getParameter("check-msa-aln") != null && request.getParameter("check-msa-aln").equalsIgnoreCase("on"))
//			asr.saveMSAAln(tempDir + "/" + asr.getLabel());

		// send output folder to client
		ZipOutputStream zout = new ZipOutputStream(response.getOutputStream());
		zipFiles(sessionDir, zout);
		zout.close();

	}

	/**
	 * Helper functions to zip files/directories
	 **/
	private void zipFiles(File folder, ZipOutputStream zout) throws IOException {
		for (File file : folder.listFiles()) {
			if (file.isFile()) {
				zout.putNextEntry(new ZipEntry(file.getName()));
				FileInputStream fis = new FileInputStream(file);
				IOUtils.copy(fis, zout);
				fis.close();
				zout.closeEntry();
			}
		}
	}

	private String checkErrors(ASR asr) {
		String message = null;
		if (!asr.getLoaded())
			if ((asr.getData() == null || asr.getData().equalsIgnoreCase("") || asr.getData().equalsIgnoreCase("none"))
					&& (asr.getSeqFile() == null || asr.getSeqFile().getOriginalFilename().equalsIgnoreCase("")) &&
					(asr.getAlnFile() == null || asr.getAlnFile().getOriginalFilename().equalsIgnoreCase("")))
				message = "No sequence or alignment file specified.";
			else if ((asr.getSeqFile() != null && !asr.getSeqFile().getOriginalFilename().endsWith(".aln") &&
					!asr.getSeqFile().getOriginalFilename().endsWith(".fa") && !asr.getSeqFile().getOriginalFilename().endsWith(".fasta")) ||
					(asr.getAlnFile() != null && !asr.getAlnFile().getOriginalFilename().endsWith(".aln") &&
						!asr.getAlnFile().getOriginalFilename().endsWith(".fa") && !asr.getAlnFile().getOriginalFilename().endsWith(".fasta")))
				message = "Incorrect sequence or alignment format (requires FASTA or Clustal format .aln, .fa or .fasta).";
			else if (((asr.getSeqFile() != null && !asr.getSeqFile().getOriginalFilename().equalsIgnoreCase("")) ||
					(asr.getAlnFile() != null && !asr.getAlnFile().getOriginalFilename().equalsIgnoreCase(""))) &&
					(asr.getTreeFile() == null || asr.getTreeFile().getOriginalFilename().equalsIgnoreCase("")))
				message = "No phylogenetic tree file specified.";
			else if (asr.getTreeFile() != null && !asr.getTreeFile().getOriginalFilename().endsWith(".nwk"))
				message = "Incorrect phylogenetic tree format (requires Newick format .nwk).";
		return message;
	}

}
