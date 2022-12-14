package br.ufes.inf.nemo.ontoumltodb.transformation.approaches.process;

import java.util.ArrayList;

import br.ufes.inf.nemo.ontoumltodb.transformation.graph.MissingConstraintData;
import br.ufes.inf.nemo.ontoumltodb.transformation.graph.Graph;
import br.ufes.inf.nemo.ontoumltodb.transformation.graph.GraphAssociation;
import br.ufes.inf.nemo.ontoumltodb.transformation.graph.GraphGeneralization;
import br.ufes.inf.nemo.ontoumltodb.transformation.graph.GraphGeneralizationSet;
import br.ufes.inf.nemo.ontoumltodb.transformation.graph.Node;
import br.ufes.inf.nemo.ontoumltodb.transformation.graph.NodeProperty;
import br.ufes.inf.nemo.ontoumltodb.transformation.graph.NodePropertyEnumeration;
import br.ufes.inf.nemo.ontoumltodb.transformation.tracer.TraceTable;
import br.ufes.inf.nemo.ontoumltodb.util.Cardinality;
import br.ufes.inf.nemo.ontoumltodb.util.Increment;
import br.ufes.inf.nemo.ontoumltodb.util.IndexType;
import br.ufes.inf.nemo.ontoumltodb.util.MissingConstraint;
import br.ufes.inf.nemo.ontoumltodb.util.Origin;
import br.ufes.inf.nemo.ontoumltodb.util.Util;

public class Lifting {

	public static void run(Node node, Graph graph, TraceTable traceTable) {
		
		if(node.getGeneralizationSets().size() == 0)
			resolveGeneralization(node, graph, traceTable);
		else resolveGeneralizationSet(node, graph, traceTable);
	}
	
	// **************************************************************************************
	// *********** Resolve the nodes generalizations
	// **************************************************************************************
	private static void resolveGeneralization(Node node, Graph graph, TraceTable traceTable) {
		NodeProperty newProperty;
		boolean hasMultipleInheritance = false;
		
		if(node.hasMultipleInheritance()) {
			hasMultipleInheritance = true;
		}
		
		for(GraphGeneralization generalization : node.getGeneralizations()) {
			// create a boolean for the specialization
			newProperty = new NodeProperty(
					generalization.getGeneral(), 
					Increment.getNextS(),
					"is" + generalization.getSpecific().getName(), 
					"boolean", 
					false, 
					false);
			newProperty.setIdentifyOtherClass(true);
			newProperty.setDefaultValue(false);
			newProperty.setIndexType(IndexType.INDEX);
			newProperty.setGeneratedFromTransformationProcess(true);
			newProperty.setRecivedBy(Origin.LIFTING);
	
			// The new property is put in the generalization node.
			generalization.getGeneral().addProperty(newProperty);
			
			// Lifting the attributes
			liftAttributes(node, generalization.getGeneral(), newProperty, "true");
			
			// Lifting the constraints
			addLostConstraintM6(node, traceTable);
			liftConstraints(node, generalization.getGeneral());
			
			// for tracing
			if(hasMultipleInheritance) {
				traceTable.addTargetNode(node, generalization.getGeneral(), newProperty, true);
			}
			else {
				traceTable.updateTrace(node, generalization.getGeneral(), newProperty, true);
			}
			
			remakeReferences(node, generalization.getGeneral(), traceTable, graph);
		}
		if(hasMultipleInheritance) {
			traceTable.moveFilters(node);
			traceTable.removeTracedNode(node);
		}
		removeNodeAssociations(node, graph);
		
		graph.removeNode(node);
	}
	
	// **************************************************************************************
	// *********** Resolve the node generalization sets
	// **************************************************************************************
	private static void resolveGeneralizationSet(Node node, Graph graph, TraceTable traceTable) {
		NodePropertyEnumeration newEnumerationField;

		for (GraphGeneralizationSet gs : node.getGeneralizationSets()) {			
			newEnumerationField = createEnumerationColumn(gs);
			newEnumerationField.setIdentifyOtherClass(true);
			gs.getGeneral().addProperty(newEnumerationField);
			
			for (Node specializationNode : gs.getSpecializationNodes()) {
				
				// Lifting the attributes
				liftAttributes(specializationNode, gs.getGeneral(), newEnumerationField, specializationNode.getName());
				
				// Lifting the constraints
				addLostConstraintM6(specializationNode, traceTable);
				liftConstraints(specializationNode, gs.getGeneral());
				
				// for the tracing
				traceTable.updateTrace(specializationNode, gs.getGeneral(), newEnumerationField, specializationNode.getName());
				
				remakeReferences(specializationNode, gs.getGeneral(), traceTable, graph);
				removeNodeAssociations(specializationNode, graph);
				
				if(!graph.belongAnotherGS(specializationNode)) {
					graph.removeNode(specializationNode);
				}
			}
			
			gs.setResolved(true);
			graph.removeGeneralizationSet(gs);
		}
	}
	
	private static NodePropertyEnumeration createEnumerationColumn(GraphGeneralizationSet gs) {
		String enumName = getEnumName(gs) + "Enum";
		
		NodePropertyEnumeration newEnumeration = new NodePropertyEnumeration(
					gs.getGeneral(), 
					Increment.getNextS(), 
					enumName,
					"string", 
					false, 
					false);
		
		newEnumeration.setGeneratedFromTransformationProcess(true);
		newEnumeration.setOriginGeneralizationSet(gs);
		
		if(gs.isComplete())
			newEnumeration.setNullable(false);
		else newEnumeration.setNullable(true);
		
		if(gs.isDisjoint())
			newEnumeration.setMultivalued(false);
		else newEnumeration.setMultivalued(true);
					
		for (Node specializationNode : gs.getSpecializationNodes()) {
			newEnumeration.addValue(specializationNode.getName());
		}
		return newEnumeration;
	}
	
	// **************************************************************************************
	// *********** Resolve the node attributes
	// **************************************************************************************
	// must be called after creating all attributes on the specialization nodes.
	private static void liftAttributes(Node subnode, Node superNode, NodeProperty mandatoryProperty, String mandatoryValue) {
		ArrayList<NodeProperty> newProperties = new ArrayList<NodeProperty>();
		NodeProperty newProperty;

		for (NodeProperty property : subnode.getProperties()) {
			newProperty = property.clone(superNode, Increment.getNextS());
			newProperty.setOwnerNode(superNode);
			if (newProperty.getDefaultValue() == null) {
				// Does not change nullability for columns with default values (eg is_employee
				// default false)
				newProperty.setNullable(true);
			}
			if(newProperty.getRecivedBy() == Origin.CREATION) { // Change only one time
				newProperty.setRecivedBy(Origin.LIFTING);
			}
			
			if(!newProperty.hasMandatoryProperty())
				newProperty.setMandatoryProperty(mandatoryProperty, property.isNullable() ? false : true, mandatoryValue);
			
			newProperties.add(newProperty);
		}
		superNode.addProperties(newProperties);
		superNode.atualizeMandatoryProperties();
	}
	
	// **************************************************************************************
	// *********** Resolve the references
	// **************************************************************************************
	private static void remakeReferences(Node node, Node superNode, TraceTable traceTable, Graph graph) {
		GraphAssociation newAssociation, originalAssociation;
		Boolean existsAssociation = false;
		
		for(GraphAssociation association : node.getAssociations()) {
			if (association.getSourceNode() == node) {
				existsAssociation = Util.existsAssociationInNewNode(superNode, association.getTargetNode(), association);
			} else {
				existsAssociation = Util.existsAssociationInNewNode(superNode,  association.getSourceNode(), association);
			}
			
			if(! existsAssociation) {
			
				newAssociation = association.clone( Integer.toString(Increment.getNext()), null, null);
				//The original association is the association of the source conceptual model, not of the current graph
				originalAssociation = traceTable.getOriginalGraph().getAssociationByID(association.getID());
				originalAssociation = originalAssociation != null ? originalAssociation : association.getOriginalAssociation();
				newAssociation.setOriginalAssociation(originalAssociation);
				
				//for self-associations
				if(newAssociation.getSourceNode().isMyId(newAssociation.getTargetNode().getID())) {
					newAssociation.setSourceNode(superNode);
					newAssociation.setTargetNode(superNode);
					newAssociation.setTargetCardinality(getNewCardinality(association.getTargetCardinality()));
					newAssociation.setSourceCardinality(getNewCardinality(association.getSourceCardinality()));
				}
				else {
					if (newAssociation.getSourceNode().getName().equals(node.getName())) {
						newAssociation.setSourceNode(superNode);
						newAssociation.setTargetCardinality(getNewCardinality(association.getTargetCardinality()));
					} else {
						newAssociation.setTargetNode(superNode);
						newAssociation.setSourceCardinality(getNewCardinality(association.getSourceCardinality()));
					}
				}
				
				superNode.addAssociation(newAssociation);
				
				graph.addAssociation(newAssociation);
			}
		}
	}
	
	private static void removeNodeAssociations(Node node, Graph graph) {
		GraphAssociation association;
	
		while (node.getAssociations().size() != 0) {
			association = node.getAssociations().get(0);
			node.removeAssociation(association);
			graph.removeAssociation(association);
		}
	}
	
	private static Cardinality getNewCardinality(Cardinality oldCardinality) {
		if (oldCardinality == Cardinality.C1_N) {
			return Cardinality.C0_N;
		} else if (oldCardinality == Cardinality.C1) {
			return Cardinality.C0_1;
		} else
			return oldCardinality;
	}
	
	private static String getEnumName(GraphGeneralizationSet gs) {
		if (gs.getName() == null || gs.getName().trim().equals(""))
			return gs.getGeneral().getName() + "Type";
		else
			return gs.getName();
	}
	
	private static void liftConstraints(Node sourceNode, Node targetNode) {
		for(MissingConstraintData data : sourceNode.getAllMissingConstraint()) {
			targetNode.addMissingConstraint(data.getSourceNode(), data.getSourceAssociation(), data.getMissingConstraint());
		}
	}
	
	private static void addLostConstraintM6(Node liftedNode, TraceTable traceTable) {
		Node relatedNode;
//		GraphAssociation originalAssociation;
		Cardinality cardinalityBegin;
		Cardinality cardinalityEnd;
		
		for(GraphAssociation association : liftedNode.getAssociations()) {
//			originalAssociation = association.getOriginalAssociation();
			cardinalityBegin = association.getCardinalityBeginOf(liftedNode);
			cardinalityEnd = association.getCardinalityEndOf(liftedNode);
//			cardinalityBegin = originalAssociation.getCardinalityBeginOf(liftedNode);
//			cardinalityEnd = originalAssociation.getCardinalityEndOf(liftedNode);
			
			if(		(cardinalityBegin == Cardinality.C0_1 && cardinalityEnd == Cardinality.C0_N) ||
					(cardinalityBegin == Cardinality.C0_1 && cardinalityEnd == Cardinality.C1_N) ||
					(cardinalityBegin == Cardinality.C1   && cardinalityEnd == Cardinality.C0_N) ||
					(cardinalityBegin == Cardinality.C1   && cardinalityEnd == Cardinality.C1_N) ||
					(cardinalityBegin == Cardinality.C1   && cardinalityEnd == Cardinality.C0_1)
			) {
				relatedNode = association.getNodeEndOf(liftedNode);
//				relatedNode = originalAssociation.getNodeEndOf(liftedNode);
//				relatedNode = traceTable.getTracesById(relatedNode).get(0).getMainNode();				
				relatedNode.addMissingConstraint(liftedNode, association, MissingConstraint.MC6);
			}
			else {
				if(		(cardinalityBegin == Cardinality.C0_N && cardinalityEnd == Cardinality.C0_1) ||
						(cardinalityBegin == Cardinality.C1_N && cardinalityEnd == Cardinality.C0_1) ||
						(cardinalityBegin == Cardinality.C0_N && cardinalityEnd == Cardinality.C1)   ||
						(cardinalityBegin == Cardinality.C1_N && cardinalityEnd == Cardinality.C1)   ||
						(cardinalityBegin == Cardinality.C0_1 && cardinalityEnd == Cardinality.C1)
				) {
					//relatedNode = association.getNodeEndOf(liftedNode);
					//relatedNode.addMissingConstraint(liftedNode, association, MissingConstraint.MC6);
					liftedNode.addMissingConstraint(liftedNode, association, MissingConstraint.MC6_Inverse);
				}
				else {
					if(		(cardinalityBegin == Cardinality.C0_1 && cardinalityEnd == Cardinality.C0_1) 
					) {
						relatedNode = association.getNodeEndOf(liftedNode);
//						relatedNode = originalAssociation.getNodeEndOf(liftedNode);
//						relatedNode = traceTable.getTracesById(relatedNode).get(0).getMainNode();
						relatedNode.addMissingConstraint(liftedNode, association, MissingConstraint.MC6);
						liftedNode.addMissingConstraint(liftedNode, association, MissingConstraint.MC6);
					}
				}
			}
			//  N:N associations is produced by the trace table only
		}
	}
}
