package br.ufes.inf.nemo.ontoumltodb.transformation;

import java.util.ArrayList;

import br.ufes.inf.nemo.ontoumltodb.transformation.approaches.IStrategy;
import br.ufes.inf.nemo.ontoumltodb.transformation.approaches.OneTablePerClass;
import br.ufes.inf.nemo.ontoumltodb.transformation.approaches.OneTablePerConcreteClass;
import br.ufes.inf.nemo.ontoumltodb.transformation.approaches.OneTablePerKind;
import br.ufes.inf.nemo.ontoumltodb.transformation.approaches.OneTablePerLeafClass;
import br.ufes.inf.nemo.ontoumltodb.transformation.convert2er.ToEntityRelationship;
import br.ufes.inf.nemo.ontoumltodb.transformation.database.BuildDatabase;
import br.ufes.inf.nemo.ontoumltodb.transformation.database.trigger.TriggerResult;
import br.ufes.inf.nemo.ontoumltodb.transformation.graph.Graph;
import br.ufes.inf.nemo.ontoumltodb.transformation.obda.GenerateObdaConnection;
import br.ufes.inf.nemo.ontoumltodb.transformation.obda.GenerateObda;
import br.ufes.inf.nemo.ontoumltodb.transformation.tracer.TraceTable;
import br.ufes.inf.nemo.ontoumltodb.util.DbmsSupported;
import br.ufes.inf.nemo.ontoumltodb.util.MappingStrategy;

public class OntoUmlToDb {

	private String projectName;
	private Graph graph;
	private Graph intermediateGraph;
	private TraceTable traceTable;

	private MappingStrategy mappingStrategy;
	private DbmsSupported dbms;
	private boolean isStandardizeNames;
	private boolean isEnumFieldToLookupTable;
	private boolean isPutInheritedAttributes;
	private boolean isTransformaNtoNFirst;
	private String hostName;
	private String databaseName;
	private String userName;
	private String password;
	private String baseIri;

	public OntoUmlToDb(Graph graph) {
		this.graph = graph;
		this.traceTable = new TraceTable(graph);
		
		this.projectName = "UnnamedProject";
		this.mappingStrategy = MappingStrategy.ONE_TABLE_PER_CLASS;
		this.dbms = DbmsSupported.GENERIC_SCHEMA;
		this.isStandardizeNames = false;
		this.isEnumFieldToLookupTable = false;
		this.isPutInheritedAttributes = false;
		this.isTransformaNtoNFirst = true;
		this.hostName = "host name not informed";
		this.databaseName = "database name not informed";
		this.userName = "user name not informed";
		this.password = "password not informed";
		this.baseIri = "IRI not informed";
	}

	public void runTransformation() {
		//We use the SQL 1999:standard. In this standard there is no enumeration type attribute.
		if(this.dbms == DbmsSupported.GENERIC_SCHEMA)
			this.isEnumFieldToLookupTable = true;
		
		prepare();
		doTransformations();
		
		getRelationalSchemaScript();
		getTriggersScripts();
	}
	
	/*
	 * The Graph is prepared to perform the transformation with enumerations attributes, not 
	 * with enumerations associations
	 */
	private void prepare() {
		graph.transformEnumAssociationsInProperties();
		graph.transformDatatypeAssociationInProperties();
//		graph.transform1To1AssociationIn1To01(); // moved to ToEntityRelatinship class
	}

	private void doTransformations() {
		IStrategy strategy;

		switch (mappingStrategy) {
		case ONE_TABLE_PER_KIND:
			strategy = new OneTablePerKind();
			break;
		case ONE_TABLE_PER_CLASS:
			strategy = new OneTablePerClass();
			break;
		case ONE_TABLE_PER_CONCRETE_CLASS:
			strategy = new OneTablePerConcreteClass();
			break;
		case ONE_TABLE_PER_LEAF_CLASS:
			strategy = new OneTablePerLeafClass();
			break;
		default:
			strategy = new OneTablePerClass();
			break;
		}
		strategy.setTransformaNtoNFirst(isTransformaNtoNFirst);
		strategy.run(graph, traceTable);
		
		// Save the graph (class diagram) before being transformed to entity-relationship
		intermediateGraph = graph.clone();
		
		ToEntityRelationship.run(graph, traceTable, isStandardizeNames, isEnumFieldToLookupTable);
	}

	public Graph getGraph() {
		return this.graph;
	}
	
	public String getStringTrace() {
		return traceTable.toString();
	}
	
	public String getRelationalSchemaScript() {
		return BuildDatabase.getSchema(graph, dbms);
	}
	
	public String getIndexesScript() {
		return BuildDatabase.getIndexes(graph, dbms);
	}

	public String getObdaScript() {
		return GenerateObda.generateMapping(projectName, traceTable, baseIri);
	}

	public String getConnectionScript() {
		return GenerateObdaConnection.getConnection(dbms, hostName, databaseName, userName, password);
	}
	
	public String getViewsScript() {
		return BuildDatabase.getViews(traceTable, isPutInheritedAttributes, dbms);
	}
	
	public ArrayList<TriggerResult> getTriggersScripts() {
		return BuildDatabase.getTriggers(graph, traceTable, dbms);
	}
	
	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public void setMappingStrategy(MappingStrategy mappingStrategy) {
		this.mappingStrategy = mappingStrategy;
	}
	
	public void setDbms(DbmsSupported dbms) {
		this.dbms = dbms;
	}

	public void setStandardizeNames(boolean flag) {
		this.isStandardizeNames = flag;
	}

	public void setEnumFieldToLookupTable(boolean flag) {
		this.isEnumFieldToLookupTable = flag;
	}
	
	public void setPutInheritedAttributes(boolean flag) {
		this.isPutInheritedAttributes = flag;
	}
	
	public void setTransformaNtoNFirst(boolean flag) {
		this.isTransformaNtoNFirst = flag;
	}
	
	public void setHostNameConnection(String hostName) {
		this.hostName = hostName;
	}
	
	public void setDatabaseNameConnection(String databaseName) {
		this.databaseName = databaseName;
	}
	
	public void setUserNameConnection(String userName) {
		this.userName = userName;
	}
	
	public void setPasswordConnection(String password) {
		this.password = password;
	}
	
	public void setBaseIri(String baseIri) {
		this.baseIri = baseIri;
	}

	public Graph getIntermediateGraph() {
		return this.intermediateGraph;
	}
	
	public void showGraph() {
		System.out.println(graph.toString());
	}
}
