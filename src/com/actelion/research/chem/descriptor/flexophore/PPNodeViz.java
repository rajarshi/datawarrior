package com.actelion.research.chem.descriptor.flexophore;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.actelion.research.chem.Coordinates;

/**
 * 
 * PPNodeViz
 * <p>Copyright: Actelion Ltd., Inc. All Rights Reserved
 * This software is the proprietary information of Actelion Pharmaceuticals, Ltd.
 * Use is subject to license terms.</p>
 * @author Modest von Korff
 * @version 1.0
 * 2006 MvK: Start implementation
 * 5 Jan 2009 MvK: Start new implementation
 * 22 Jan 2013 MvK: new visualization with isospheres in jMol.
 */
public class PPNodeViz extends PPNode implements Serializable {
	
	
	private static final long serialVersionUID = 22022013;

	private static final double LIM_EQUAL_COORD=0.00001;
	
	private static final float SIMILARITY_NODES=-1;
	
	// Indices of mapping original  atoms in molecule, used for comparison
	// Only together with MolDistHistViz and visualization of the structure and the descriptor.
	private HashSet<Integer> hsIndexOriginalAtoms;
	
	private Coordinates coordinates;
	
	private float similarityMappingNodes;

	// Just an index used for the mapping of two nodes in two different MolDistHistViz objects.
	private int mappingIndex;
	
	// Index to track the fate of the nodes.
	private byte index;
	
	// Corresponding count for the isosphere in jmol.  
	private int indexSphereViz;
	
	private boolean marked;
	
	public PPNodeViz(){
		super(new PPNode()); 
		init();
	}
	public PPNodeViz(PPNodeViz node){
		copy(node);
	}
	
	public PPNodeViz(Coordinates coord, int interactionID, int indOrigAtom){
		super(interactionID);
		
		init();
		
		coordinates = new Coordinates(coord);
		
		hsIndexOriginalAtoms.add(indOrigAtom);	
	}
	
	public PPNodeViz(PPNode node){
		super(node);
		init();
	}
	
	private void init(){
		
		coordinates = new Coordinates();
		
		hsIndexOriginalAtoms = new HashSet<Integer>();
		
		mappingIndex = INFO_DEFAULT;
		
		index = INFO_DEFAULT;

		similarityMappingNodes = SIMILARITY_NODES;
		
	}
	
	public int getIndex() {
		return index;
	}

	public void setIndex(int id) {
		index = (byte)id;
	}

	public void set(PPNode node){
		super.set(node);
	}
	
	/**
	 * Only atoms are added that are not yet in the list,
	 * check PPAtom.equals for comparison.
	 * @param node
	 */
	public void addAtoms(PPNodeViz node){
		super.addAtoms(node);
		hsIndexOriginalAtoms.addAll(node.hsIndexOriginalAtoms);
	}
	
	public void addIndexOriginalAtom(int index){
		if(hsIndexOriginalAtoms==null)
			hsIndexOriginalAtoms = new HashSet<Integer>();
		
		hsIndexOriginalAtoms.add(index);
	}
	
	/**
	 * Copy of node into this
	 * @param node
	 */
	public void copy(PPNodeViz node){
		super.copy(node);
		
		coordinates = new Coordinates(node.coordinates);
		
		mappingIndex = node.mappingIndex;

		hsIndexOriginalAtoms = new HashSet<Integer>();
		
		hsIndexOriginalAtoms.addAll(node.hsIndexOriginalAtoms);
		
		index = node.index;
		
		indexSphereViz = node.indexSphereViz;
		
		similarityMappingNodes = node.similarityMappingNodes;
		
		marked = node.marked;

	}
	
	/**
	 * @return the marked
	 */
	public boolean isMarked() {
		return marked;
	}
	/**
	 * @param marked the marked to set
	 */
	public void setMarked(boolean marked) {
		this.marked = marked;
	}
	
	public PPNodeViz getCopy(){
		return new PPNodeViz(this);
	}
	
	public List<Integer> getListIndexOriginalAtoms() {
		return new ArrayList<Integer>(hsIndexOriginalAtoms);
	}
	
	public int getMappingIndex() {
		return mappingIndex;
	}
	
	public void setMappingIndex(int info) {
		this.mappingIndex = info;
	}
	
	public void clearInfo() {
		mappingIndex = INFO_DEFAULT ;
	}

	public boolean hasSamePosition(PPNodeViz node){
		return equal(coordinates, node.coordinates);
	}
	


	/**
	 * 26.03.2007 MvK 
	 * Own equal function for coordinates. In Joels fct seems to be an 
	 * error with rounding in insignificant digits.
	 * @param c1
	 * @param c2
	 * @return
	 */
	private boolean equal(Coordinates c1, Coordinates c2){
		boolean bEq = true;
		
		if(Math.abs(c1.x-c2.x)>LIM_EQUAL_COORD)
			bEq = false;
		else if(Math.abs(c1.y-c2.y)>LIM_EQUAL_COORD)
			bEq = false;
		else if(Math.abs(c1.z-c2.z)>LIM_EQUAL_COORD)
			bEq = false;
		
		return bEq;
	}
	
	public void resetInfoColor(){
		mappingIndex = INFO_DEFAULT;
	}

	
	public Coordinates getCoordinates() {
		return coordinates;
	}

	public void setCoordinates(double x, double y, double z) {
		coordinates.x = x;
		coordinates.y = y;
		coordinates.z = z;
	}
	public void setCoordinatesNull() {
		coordinates=null;
	}
	public double getX(){
		return coordinates.x;
	}
	public double getY(){
		return coordinates.y;
	}
	public double getZ(){
		return coordinates.z;
	}
	public void setX(double x){
		coordinates.x = x;
	}
	public void setY(double y){
		coordinates.y = y;
	}
	public void setZ(double z){
		coordinates.z = z;
	}
	public int getIndexSphereVisualization() {
		return indexSphereViz;
	}
	protected void setIndexSphereVisualization(int indexSphereViz) {
		this.indexSphereViz = indexSphereViz;
	}
	
	/**
	 * @return the similarityMappingNodes
	 */
	public float getSimilarityMappingNodes() {
		return similarityMappingNodes;
	}
	/**
	 * @param similarityMappingNodes the similarityMappingNodes to set
	 */
	public void setSimilarityMappingNodes(float similarityMappingNodes) {
		this.similarityMappingNodes = similarityMappingNodes;
	}
	
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		
		sb.append(super.toStringLong());
		
		sb.append(", coord ");
		sb.append(coordinates.toString());
		
		sb.append(", mapping color ");
		sb.append(mappingIndex);
		
		sb.append(", index ");
		sb.append(index);
		sb.append(", indexAtom viz ");
		sb.append(indexSphereViz);
		
		sb.append(")");
		
		return sb.toString();
	}

	public String toStringShort(){
		
		return super.toString();
	}

}
