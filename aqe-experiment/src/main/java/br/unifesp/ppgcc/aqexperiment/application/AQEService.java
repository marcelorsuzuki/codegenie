package br.unifesp.ppgcc.aqexperiment.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.unifesp.ppgcc.aqexperiment.domain.AnaliseFunction;
import br.unifesp.ppgcc.aqexperiment.domain.AnaliseFunctionResponse;
import br.unifesp.ppgcc.aqexperiment.domain.Execution;
import br.unifesp.ppgcc.aqexperiment.domain.SolrResult;
import br.unifesp.ppgcc.aqexperiment.domain.SurveyResponse;
import br.unifesp.ppgcc.aqexperiment.infrastructure.AnaliseFunctionRepository;
import br.unifesp.ppgcc.aqexperiment.infrastructure.AnaliseFunctionResponseRepository;
import br.unifesp.ppgcc.aqexperiment.infrastructure.ExecutionRepository;
import br.unifesp.ppgcc.aqexperiment.infrastructure.SurveyResponseRepository;
import br.unifesp.ppgcc.aqexperiment.infrastructure.sourcereraqe.SourcererQueryBuilder;
import br.unifesp.ppgcc.aqexperiment.infrastructure.util.ConfigProperties;
import br.unifesp.ppgcc.aqexperiment.infrastructure.util.LogUtils;
import edu.uci.ics.sourcerer.services.search.adapter.SearchAdapter;
import edu.uci.ics.sourcerer.services.search.adapter.SearchResult;
import edu.uci.ics.sourcerer.services.search.adapter.SingleResult;

@Service
@Transactional
public class AQEService {

	@Autowired
	private ExecutionRepository executionRepository;

	@Autowired
	private AnaliseFunctionRepository analiseFunctionRepository;

	@Autowired
	private AnaliseFunctionResponseRepository analiseFunctionResponseRepository;

	@Autowired
	private SurveyResponseRepository surveyResponseRepository;
	
	private List<SurveyResponse> surveyResponses;
	private List<AnaliseFunction> analiseFunctions;

	public void execute() throws Exception {

		//Execution
		Execution execution = new Execution(System.currentTimeMillis());
		execution = executionRepository.save(execution);

		// SurveyResponses
		this.persistSurveyResponses(execution);
		surveyResponses = surveyResponseRepository.findAll(execution);

		analiseFunctions = analiseFunctionRepository.findAllHardCode();

		for (AnaliseFunction function : analiseFunctions) {
			if(!this.isValidFunction(function))
				continue;
			this.buildRelevants(function);
			this.buildResponses(function, execution);
			this.processResponses(function);

			analiseFunctionRepository.save(function);
		}
	}

	private boolean isValidFunction(AnaliseFunction analiseFunction) throws Exception {
		if(new Boolean(ConfigProperties.getProperty("aqExperiment.moreOneRelevant")) && analiseFunction.getRelevantsSolrIds().length <= 1)
			return false;
		return true;
	}
	
	private void persistSurveyResponses(Execution execution) throws Exception {
		for (SurveyResponse surveyResponse : surveyResponseRepository.findAllFromSheet()) {
			if (!surveyResponse.isValid())
				continue;

			surveyResponse.setExecution(execution);
			surveyResponseRepository.save(surveyResponse);
		}

	}

	private void buildRelevants(AnaliseFunction function) throws Exception {
		SearchAdapter searchAdapter = SearchAdapter.create(ConfigProperties.getProperty("aqExperiment.sourcerer.url"));
		SearchResult searchResult = null;
		for (long entityId : function.getRelevantsSolrIds()) {
			String query = "entity_id:" + entityId;
			searchResult = searchAdapter.search(query);
			if (searchResult.getNumFound() == -1) {
				LogUtils.getLogger().error("Unable to perform search: " + query);
				continue;
			}
			SingleResult singleResult = searchResult.getResults(0, 1).get(0);
			function.getRelevants().add(new SolrResult(singleResult));
		}
	}

	private void buildResponses(AnaliseFunction function, Execution execution) {
		for (SurveyResponse surveyResponse : surveyResponses)
			function.addResponse(surveyResponse, execution);
	}

	private void processResponses(AnaliseFunction function) throws Exception {
		boolean relaxReturn = new Boolean(ConfigProperties.getProperty("aqExperiment.relaxReturn"));
		boolean relaxParams = new Boolean(ConfigProperties.getProperty("aqExperiment.relaxParams"));
		SourcererQueryBuilder sourcererQueryBuilder = new SourcererQueryBuilder(ConfigProperties.getProperty("aqExperiment.expanders"), relaxReturn, relaxParams);

		SearchAdapter searchAdapter = SearchAdapter.create(ConfigProperties.getProperty("aqExperiment.sourcerer.url"));
		SearchResult searchResult = null;

		for (AnaliseFunctionResponse response : function.getResponses()) {
			List<SingleResult> results = new ArrayList<SingleResult>();

			String query = sourcererQueryBuilder.getSourcererExpandedQuery(response.getMethodName(), response.getReturnType(), response.getParams());
			searchResult = searchAdapter.search(query);
			if (searchResult.getNumFound() == -1) {
				LogUtils.getLogger().error("Unable to perform search: " + query);
			} else {
				results.addAll(searchResult.getResults(0, 100));
			}
			response.setResultsFromSingleResult(results);

			this.calculateRecallAndPrecision(response, function);
		}
	}

	private void calculateRecallAndPrecision(AnaliseFunctionResponse response, AnaliseFunction function) {

		int totalRelevants = function.getRelevants().size();
		int totalResults = response.getResults().size();
		int totalIntersections = 0;

		for (SolrResult relevant : function.getRelevants()) {
			if (response.getResults().contains(relevant))
				totalIntersections++;
		}

		double recall = totalRelevants == 0 ? 0 : new Double(totalIntersections) / new Double(totalRelevants);
		double precision = totalResults == 0 ? 0 : new Double(totalIntersections) / new Double(totalResults);

		response.setRecall(recall);
		response.setPrecision(precision);
		response.setTotalRelevants(totalRelevants);
		response.setTotalResults(totalResults);
		response.setTotalIntersections(totalIntersections);
	}

	public List<SurveyResponse> getSurveyResponses() {
		return surveyResponses;
	}

	public List<AnaliseFunction> getAnaliseFunctions() {
		return analiseFunctions;
	}
}
