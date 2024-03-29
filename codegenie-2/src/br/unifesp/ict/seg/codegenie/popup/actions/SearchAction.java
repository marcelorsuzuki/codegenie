package br.unifesp.ict.seg.codegenie.popup.actions;

import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

import br.unifesp.ict.seg.codegenie.Activator;

import br.unifesp.ict.seg.codegenie.preferences.PreferenceConstants;
import br.unifesp.ict.seg.codegenie.search.solr.SearchQueryCreator;
import br.unifesp.ict.seg.codegenie.search.solr.SolrSearch;
import br.unifesp.ict.seg.codegenie.tmp.Debug;
import br.unifesp.ict.seg.codegenie.views.ResultsViewUpdater;


public class SearchAction implements IObjectActionDelegate {

	private static Long lastQID;
	private ISelection selection;
	
	/**
	 * Constructor for Action1.
	 */
	public SearchAction() {
		super();
	}

	/**
	 * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
	 */
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		
	}

	/**
	 * @see IActionDelegate#run(IAction)
	 */
	public void run(IAction action) {
		IType selection = (IType) ((TreeSelection)this.selection).getFirstElement();
		IJavaProject javap = selection.getJavaProject();
		try {
			javap.save(null, true);
			javap.getProject().build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
		} catch (Exception e) {
			System.out.println(e.getLocalizedMessage());
		}
		//given the IType that is selected, build the solr query
		SearchQueryCreator sqc = new SearchQueryCreator(selection);
		sqc.formQuery();
		sqc.getMethodInterface();
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		boolean ensyn = store.getBoolean(PreferenceConstants.ENSYN);
		boolean codesyn = store.getBoolean(PreferenceConstants.CODESYN);
		boolean type = true;
		sqc.expandQuery(ensyn, codesyn, type);
		
		lastQID = sqc.getID();
		String query = sqc.getQuery();
		if(query == null){
			Debug.errDebug(SearchAction.this.getClass(), "Unable to build Solr query");
			return;
		}
		Debug.debug(SearchAction.this.getClass(), "Query params are:");
		Debug.debug(SearchAction.this.getClass(), "\t => "+query);	
		
		SolrSearch searchJob=null;
		try {
			searchJob = new SolrSearch(sqc.getID(),query,javap,this.selection);
		} catch (InstantiationException e) {
			e.printStackTrace();
			return;
		}

		ResultsViewUpdater rvu = new ResultsViewUpdater(searchJob,sqc);
		rvu.makeQueryAndUpdateView();
	}

	/**
	 * @see IActionDelegate#selectionChanged(IAction, ISelection)
	 */
	public void selectionChanged(IAction action, ISelection selection) {
		this.selection = selection;
	}

	public static Long getCurrentQueryID() {
		return lastQID;
	}

}
