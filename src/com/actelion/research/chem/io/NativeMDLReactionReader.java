/*
 * Copyright 2014 Actelion Pharmaceuticals Ltd., Gewerbestrasse 16, CH-4123 Allschwil, Switzerland
 *
 * This file is part of DataWarrior.
 * 
 * DataWarrior is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * DataWarrior is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with DataWarrior.
 * If not, see http://www.gnu.org/licenses/.
 *
 * @author Thomas Sander
 */

package com.actelion.research.chem.io;

import java.io.*;
import java.util.ArrayList;

import com.actelion.research.chem.AromaticityResolver;
import com.actelion.research.chem.ExtendedMolecule;
import com.actelion.research.chem.Molecule;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.chem.reaction.Reaction;
import com.actelion.research.util.DoubleFormat;

public class NativeMDLReactionReader {
    private static final int BUFFER_SIZE = 512;

    private static final int kErrNoError = 0;
    private static final int kErrGetBranchNoData = -1;
    private static final int kErrGetMolInfoNoParent = -2;
    private static final int kErrGetMolInfoNoData = -3;
    private static final int kErrVariationUnavailable = -4;

    private static final int kMaxReactants = 16;
    private static final int kMaxSolvents = 40;
    private static final int kMaxCatalysts = 40;

    private String      mDirectory;
    private DTP[]       mDTPDir;
    private SBF[]       mSBFDir;
    private DTP         mRootDTP;
    private int         mReactionCount;
    private int[]       mBuffer;
    private int         mBufferIndex;
    private int         mBitmask;
    private double      mYield;
    private Reaction    mReaction;
    private StringBuffer mReactantData,mProductData,mSolventData,mCatalystData;
    private int         mSolventCount,mCatalystCount;
    private int         mFieldCount;
    private int[]       mMolRegNo,mSolventRegNo,mCatalystRegNo;
    private ArrayList<ExtendedMolecule>   mSolvents,mCatalysts;

    public NativeMDLReactionReader(String directory) throws IOException {
        mDirectory = directory + File.separator;
        readDTP("DTPDIR.DAT");
        readSBF("SBFDIR.DAT");

        if (mDTPDir != null && mSBFDir != null) {
            for (int dtp=0; dtp<mDTPDir.length; dtp++) {
                if (mDTPDir[dtp].dtpnam.equals("VARIATION")) {
                    mRootDTP = mDTPDir[dtp];
                    DataInputStream dis = getDataInputStream(pointerfile(mRootDTP.drpoin));
                    mReactionCount = readFileSize(dis)/4 - 2;
                    }
                }
            }

        mBuffer = new int[BUFFER_SIZE];
        mSolvents = new ArrayList<ExtendedMolecule>();
        mCatalysts = new ArrayList<ExtendedMolecule>();
        mMolRegNo = new int[kMaxReactants];
        mSolventRegNo = new int[kMaxSolvents];
        mCatalystRegNo = new int[kMaxCatalysts];
        }

    public int getReactionCount() {
        return mReactionCount;
        }

    public int getVariationCount(int regNo) throws IOException {
        for (int i=0; i<mDTPDir.length; i++) {
            DTP dtp = mDTPDir[i];
            if (dtp.dtpnam.equals("VARIATION")) {
                int blocks = getData(regNo, mBuffer, dtp);
                return blocks;
                }
            }
        throw new IOException("no VARIATION data type");
        }

    private String pointerfile(int no) {
        if (no < 10)
            return "DR000"+no+".DAT";
        if (no < 100)
            return "DR00"+no+".DAT";

        return "DR0"+no+".DAT";
        }

    private String datafile(int no) {
        if (no < 10)
            return "FL000"+no+".DAT";
        if (no < 100)
            return "FL00"+no+".DAT";

        return "FL0"+no+".DAT";
        }

    private void readDTP(String filename) throws IOException {
        DataInputStream dis = getDataInputStream(filename);

        int size = readFileSize(dis)/DTP.SIZE;

        mDTPDir = new DTP[size];
        byte[] buf = new byte[20];

        for (int i=0; i<size;i++) {
            DTP dtp = mDTPDir[i] = new DTP();

            dtp.lnum = readInt(dis);
            dtp.drpoin = readInt(dis);
            dtp.parentID = readInt(dis);
            dtp.length = readInt(dis);
            dtp.typno = readInt(dis);
            dtp.security = readInt(dis);
            dtp.sbfpoin = readInt(dis);
            dtp.sbfnum = readInt(dis);
            dtp.hash = readInt(dis);
            dtp.unk5 = readInt(dis);
            dtp.unk6 = readInt(dis);
            dtp.unk7 = readInt(dis);
            dis.read(buf, 0, 20);
            dtp.dtpnam = new String(buf).trim();
            dtp.access1 = dis.readByte();
            dtp.access2 = dis.readByte();
            dis.read(dtp.empty = new byte[2], 0, 2);
            dtp.isparent = readInt(dis);
            dtp.depdata = new int[20];
            for (int j=0; j<20; j++)
                dtp.depdata[j] = readInt(dis);
            dtp.rootID = new int[4];
            for (int j=0; j<4; j++)
                dtp.rootID[j] = readInt(dis);
            dtp.unk9 = readInt(dis);
            dtp.unk10 = readInt(dis);
            dtp.drsize = readInt(dis);
            dtp.unk11 = readInt(dis);
/*          System.out.println(""+dtp.lnum+"\t"+dtp.drpoin+"\t"+dtp.parentID+"\t"+dtp.length+"\t"
                               +dtp.typno+"\t"+dtp.security+"\t"+dtp.sbfpoin+"\t"+dtp.sbfnum+"\t"
                               +dtp.hash+"\t"+dtp.unk5+"\t"+dtp.unk6+"\t"+dtp.unk7+"\t"
                               +dtp.dtpnam+"\t"+dtp.access1+"\t"+dtp.access2+"\t"+dtp.isparent+"\t"
                               +dtp.depdata[0]+"\t"+dtp.depdata[1]+"\t"+dtp.depdata[2]+"\t"+dtp.depdata[3]+"\t"
                               +dtp.depdata[4]+"\t"+dtp.depdata[5]+"\t"+dtp.depdata[6]+"\t"+dtp.depdata[7]+"\t"
                               +dtp.depdata[8]+"\t"+dtp.depdata[9]+"\t"+dtp.depdata[10]+"\t"+dtp.depdata[11]+"\t"
                               +dtp.rootID[0]+"\t"+dtp.rootID[1]+"\t"+dtp.rootID[2]+"\t"+dtp.rootID[3]+"\t"
                               +dtp.unk9+"\t"+dtp.unk10+"\t"+dtp.drsize+"\t"+dtp.unk11);
*/
            }

        dis.close();
        }

    private void readSBF(String filename) throws IOException {
        DataInputStream dis = getDataInputStream(filename);

        int size = readInt(dis);
        if (size < 0)   // REACCS format
            size = -size/SBF.SIZE;
        else            // ISIS format
            size = dis.available()/SBF.SIZE;

        mSBFDir = new SBF[size];
        byte[] buf = new byte[20];

        for (int i=0; i<size;i++) {
            SBF sbf = mSBFDir[i] = new SBF();
            dis.read(buf, 0, 20);
            sbf.shortnam = new String(buf).trim();
            dis.read(buf, 0, 20);
            sbf.format1 = new String(buf).trim();
            dis.read(buf, 0, 20);
            sbf.format2 = new String(buf).trim();
            dis.read(buf, 0, 20);
            sbf.name = new String(buf).trim();
            sbf.lnum = readInt(dis);
            sbf.type = readInt(dis);
            sbf.a2 = readInt(dis);
            sbf.a3 = readInt(dis);
            sbf.datalen = readInt(dis);
            sbf.datatyp = readInt(dis);
            sbf.a5 = readInt(dis);
            sbf.dtppoin = readInt(dis);
            sbf.a7 = readInt(dis);
            sbf.begin = readInt(dis);
            sbf.a8 = readInt(dis);
            sbf.a9 = readInt(dis);
            sbf.a10 = readInt(dis);
            sbf.a11 = readInt(dis);
            sbf.a12 = readInt(dis);
            sbf.a13 = readInt(dis);
            sbf.a14 = readInt(dis);
            sbf.a15 = readInt(dis);
            sbf.a16 = readInt(dis);
            sbf.a17 = readInt(dis);
            sbf.fieldNo = -1;
/*          System.out.println(sbf.shortnam+"\t"+sbf.format1+"\t"+sbf.format2+"\t"+sbf.name+"\t"
                              +sbf.lnum+"\t"+sbf.type+"\t"+sbf.a2+"\t"+sbf.a3+"\t"
                              +sbf.datalen+"\t"+sbf.datatyp+"\t"+sbf.a5+"\t"+sbf.dtppoin+"\t"
                              +sbf.a7+"\t"+sbf.begin+"\t"+sbf.a8+"\t"+sbf.a9+"\t"
                              +sbf.a10+"\t"+sbf.a11+"\t"+sbf.a12+"\t"+sbf.a13+"\t"
                              +sbf.a14+"\t"+sbf.a15+"\t"+sbf.a16+"\t"+sbf.a17);
*/
            }

        dis.close();
        }

    public Reaction getReaction(int reaction, int variation) throws IOException {
        int regNo = reaction + 1;
        mReaction = null;
        mYield = -1;
        int variationPointer = -1;

        mCatalysts.clear();
        mSolvents.clear();

        mSolventCount = 0;
        mCatalystCount = 0;

        for (int i=0; i<mDTPDir.length; i++) {
            DTP dtp = mDTPDir[i];
            if (dtp.dtpnam.equals("VARIATION")) {
                int blocks = getData(regNo, mBuffer, dtp);
                if (blocks == 0)
                    throw new IOException("invalid regNo");
                if (variation >= blocks)
                    throw new IOException("invalid variation");
                variationPointer = mBuffer[variation];
                break;
                }
            }

        if (variationPointer == -1)
            throw new IOException("no VARIATION data type");

        for (int i=0; i<mDTPDir.length; i++)
            if (mDTPDir[i].typno == 3)             // RXNDEF
                getReaction(regNo, mDTPDir[i]);
        
        for (int i=0; i<mDTPDir.length; i++) {
            if (mDTPDir[i].typno == 24)              // AUGUMENTED_RCBOND
                getMapping(regNo, mDTPDir[i]);
        
            if ((mDTPDir[i].typno == -19) || (mDTPDir[i].typno == -20)) {
                                                 // CATALYST or SOLVENT
                if (mDTPDir[i].rootID[2]-1 == i)
                           // SOLVENT/CATALYST is not direct descendent of VARIATION
                    getDeepCatalysts(variationPointer, mDTPDir[i]);
                else
                           // SOLVENT/CATALYST is direct descendent of VARIATION
                    getCatalysts(variationPointer, mDTPDir[i]);
                }

            try {
                if (mDTPDir[i].dtpnam.equals("RXNYIELD")) {
                    int blocks = getData(variationPointer, mBuffer, mDTPDir[i]);
                    if (blocks == 1)
                        extractFloatYield();
                    }
                }
            catch (IOException e) {}    // don't care about yield extraction errors
            }
        
        try {
            if (mYield == -1) {  // if no RXNYIELD provided look for YIELD datatypes
                                 // as descendent of PRODUCT datatypes
                int[] product = null;
                for (int i=0; i<mDTPDir.length; i++) {
                    if (mDTPDir[i].typno == -14) {  // PRODUCT datatype
                        int blocks = getData(variationPointer, mBuffer, mDTPDir[i]);
                        if (blocks != 0) {
                            product = new int[blocks];
                            for (int j=0; j<blocks; j++)
                                product[j] = mBuffer[j];
                            }
                        break;
                        }
                    }
    
                if (product != null) {
                    for (int i=0; i<mDTPDir.length; i++) {
                        if (mDTPDir[i].dtpnam.equals("YIELD")) {   // MDL databases
                            for (int j=0; j<product.length; j++) {
                                if (getData(product[j], mBuffer, mDTPDir[i]) == 1) {
                                    extractFloatYield();
                                    if (mYield != -1)
                                        break;
                                    }
                                }
                            break;
                            }
                        // CCR: ("PERCENT YIELD:",float); InfoChem: ("Percent Yield:", int)
                        if (mDTPDir[i].dtpnam.equals("PRODAT")) {
                            if (mSBFDir[mDTPDir[i].sbfpoin-1].name.equals("PERCENT YIELD:")
                             || mSBFDir[mDTPDir[i].sbfpoin-1].name.equals("Percent Yield:")) {
                                for (int j=0; j<product.length; j++) {
                                    if (getData(product[j], mBuffer, mDTPDir[i]) == 1) {
                                        if (mSBFDir[mDTPDir[i].sbfpoin-1].type == 1)
                                            extractFloatYield();
                                        else if (mSBFDir[mDTPDir[i].sbfpoin-1].type == 4)
                                            extractIntYield();
                                        if (mYield != -1)
                                            break;
                                        }
                                    }
                                break;
                                }
                            }
                        }
                    }
                }
            }
        catch (IOException e) {}    // don't care about yield extraction errors

        return mReaction;
        }

    public ArrayList<ExtendedMolecule> getCatalysts() {
        return mCatalysts;
        }

    public ArrayList<ExtendedMolecule> getSolvents() {
        return mSolvents;
        }

    public String getReactantData() {
        return mReactantData.length() == 0 ? null : mReactantData.toString();
        }

    public String getProductData() {
        return mProductData.length() == 0 ? null : mProductData.toString();
        }

    public String getSolventData() {
        return mSolventData.length() == 0 ? null : mSolventData.toString();
        }

    public String getCatalystData() {
        return mCatalystData.length() == 0 ? null : mCatalystData.toString();
        }

    public String[] getFieldNames() {
        mFieldCount = 0;
        for (int i=0; i<mDTPDir.length; i++)
            if (mDTPDir[i].parentID == -2)             // reaction datatypes
                if (mDTPDir[i].typno == 0)             // no special datatypes
                    getFieldNameBranch(mDTPDir[i]);

        String[] fieldName = new String[mFieldCount];
        for (int i=0; i<mSBFDir.length; i++) {
            if (mSBFDir[i].fieldNo != -1)
                fieldName[mSBFDir[i].fieldNo] = mDTPDir[mSBFDir[i].dtppoin-1].dtpnam
                      + ((mSBFDir[i].name.length() != 0) ? "."+mSBFDir[i].name
                       : mSBFDir[i].shortnam.equals("*") ? "" : "."+mSBFDir[i].shortnam);
            }
        return fieldName;
        }

    private void getFieldNameBranch(DTP dtp) {
        if (dtp.isparent != 0) {
            for (int count=0; count<20; count++) {
                int deepdtp = dtp.depdata[count] - 1;
                if (deepdtp == -1)
                    break;

                if (dtp.typno == 0)
                    getFieldNameBranch(mDTPDir[deepdtp]);
/*              else if (dtp.typno == -13)         // REACTANT
                    getMoleculeFieldNameBranch(mDTPDir[deepdtp]);
                else if (dtp.typno == -14)         // PRODUCT
                    getMoleculeFieldNameBranch(mDTPDir[deepdtp]);
                else if (dtp.typno == -19)         // SOLVENT
                    getMoleculeFieldNameBranch(mDTPDir[deepdtp]);
                else if (dtp.typno == -20)         // CATALYST
                    getMoleculeFieldNameBranch(mDTPDir[deepdtp]);
*/              }
            }
        else {
            for (int count=0; count<dtp.sbfnum; count++) {
                int sbf = dtp.sbfpoin + count - 1;
                mSBFDir[sbf].fieldNo = mFieldCount++;
                }
            }
        }

    public String[] getFieldData(int reaction, int variation) {
        mReactantData = new StringBuffer();
        mProductData = new StringBuffer();
        mSolventData = new StringBuffer();
        mCatalystData = new StringBuffer();

        int regNo = reaction + 1;
        String[] fieldData = new String[mFieldCount];
        for (int i=0; i<mDTPDir.length; i++)
            if (mDTPDir[i].parentID == -2)             // reaction datatypes
                if (mDTPDir[i].typno == 0)             // no special datatypes
                    getBranch(regNo, mDTPDir[i], variation, fieldData);
        
        for (int i=0; i<mDTPDir.length; i++) {
            if (mDTPDir[i].parentID == -1) {           // molecule datatypes
                if (mDTPDir[i].typno == 0) {           // no special datatypes
                    for (int mol=0; mol<mReaction.getReactants(); mol++)
                        if (mMolRegNo[mol] != 0)
                            putMolText(mMolRegNo[mol], mReactantData, mol, mDTPDir[i]);
                    for (int mol=0; mol<mReaction.getProducts(); mol++)
                        if (mMolRegNo[mReaction.getReactants()+mol] != 0)
                            putMolText(mMolRegNo[mReaction.getReactants()+mol], mProductData, mol, mDTPDir[i]);
                    for (int mol=0; mol<mSolventCount; mol++)
                        if (mSolventRegNo[mol] != 0)
                            putMolText(mSolventRegNo[mol], mSolventData, mol, mDTPDir[i]);
                    for (int mol=0; mol<mCatalystCount; mol++)
                        if (mCatalystRegNo[mol] != 0)
                            putMolText(mCatalystRegNo[mol], mCatalystData, mol, mDTPDir[i]);
                    }
                }
            }

        return fieldData;
        }

    private int getBranch(int entry, DTP dtp, int variation, String[] fieldData) {
        int[] data = new int[1024];

        int blocks = 0;
        try {
            blocks = getData(entry, data, dtp);
            }
        catch (IOException e) {}

        if (blocks == 0)
            return kErrGetBranchNoData;
        
        if (dtp.dtpnam.equals("VARIATION")) {
            if (variation >= blocks)
                return kErrVariationUnavailable;

            data[0] = data[variation];
            blocks = 1;
            }

        int indentation = 0;
        for (int i=1; i<4; i++)
            if (dtp.rootID[i] != 0)
                indentation++;

        if (dtp.isparent != 0) {
            for (int eintrag=0; eintrag<blocks; eintrag++) {
                for (int count=0; count<20; count++) {
                    int deepdtp = dtp.depdata[count] - 1;
                    if (deepdtp == -1)
                        break;

                    if (dtp.typno == 0)
                        getBranch(data[2*eintrag], mDTPDir[deepdtp], variation, fieldData);
                    else if (dtp.typno == -13)         // REACTANT
                        putMolText(data[2*eintrag], mReactantData, eintrag, mDTPDir[deepdtp]);
                    else if (dtp.typno == -14)         // PRODUCT
                        putMolText(data[2*eintrag], mProductData, eintrag, mDTPDir[deepdtp]);
                    else if (dtp.typno == -19)         // SOLVENT
                        putMolText(data[2*eintrag], mSolventData, eintrag, mDTPDir[deepdtp]);
                    else if (dtp.typno == -20)         // CATALYST
                        putMolText(data[2*eintrag], mCatalystData, eintrag, mDTPDir[deepdtp]);
                    }
                }
            if (dtp.typno == -19)
                mSolventCount = Math.min(mSolventCount + blocks, kMaxSolvents);
            if (dtp.typno == -20)
                mCatalystCount = Math.min(mCatalystCount + blocks, kMaxCatalysts);
            }
        else {
            int offset = 0;
            for (int block=0; block<blocks; block++) {
                for (int count=0; count<dtp.sbfnum; count++) {
                    int sbf = dtp.sbfpoin + count - 1;
                    if (dtp.length == 0)
                        if (mSBFDir[sbf].begin > data[offset])
                            break;
                    int datapoin = offset+mSBFDir[sbf].begin-(dtp.length == 0 ? 0 : 1);
                    if (mSBFDir[sbf].type == 1) {      // float-float range
                        if (mSBFDir[sbf].format2.length() == 0 || data[datapoin] == 0x20202020)
                            continue;

                        String text = filterText(formatedString(data, datapoin, mSBFDir[sbf]));
                        appendFieldData(fieldData, mSBFDir[sbf].fieldNo, text);
                        }
                    else if (mSBFDir[sbf].type == 2) { // fixed length text
                        if (data[datapoin] == 0x80808080)
                            continue;

                        StringBuffer buf = new StringBuffer();
                        int v = 0;
                        for (int i=0; i<mSBFDir[sbf].datalen; i++) {
                            v = ((i & 3) == 0) ? data[datapoin+(i >> 2)] : v >>> 8;
                            buf.append((char)(v & 0x000000FF));
                            }

                        String text = filterText(buf.toString());
                        appendFieldData(fieldData, mSBFDir[sbf].fieldNo, text);
                        }
                    else if (mSBFDir[sbf].type == 4) {  // integer
                        if (data[datapoin] == 0x20202020)
                            continue;

                        String text = ""+data[datapoin];
                        appendFieldData(fieldData, mSBFDir[sbf].fieldNo, text);
                        }
                    else if (mSBFDir[sbf].type == 5) {  // variable length text
                        if (data[datapoin] == 0 || data[datapoin] == 0x80808080)
                            continue;

                        int length = 4*(data[offset]+1-mSBFDir[sbf].begin);
                        StringBuffer buf = new StringBuffer();
                        int v = 0;
                        for (int i=0; i<length; i++) {
                            v = ((i & 3) == 0) ? data[datapoin+(i >> 2)] : v >>> 8;
                            buf.append((char)(v & 0x000000FF));
                            }
                        String text = filterText(buf.toString());
                        appendFieldData(fieldData, mSBFDir[sbf].fieldNo, text);
                        }
                    }
                offset += (dtp.length != 0) ? dtp.length+1 : data[offset]+2;
                }

/*          if ((mReactionTextP->getArrowLines() == 0) && !strncmp(mDTPDir[dtp].dtpnam,"RXNTEXT",7)) {
                offset = 0;
                for (block=0; block<blocks; block++) {
                    length = (short)(4*(*(data+offset)));
                    tptr = (char *)(data+offset+1);
                    while (*(tptr+length-1) == ' ' && length > 0)
                        length--;
                    mReactionTextP->addArrowText( tptr, length );
                    offset += *(data+offset)+2;
                    }
                }*/
            }
        return kErrNoError;
        }

    private void appendFieldData(String[] fieldData, int index, String text) {
        if (fieldData[index] == null)
            fieldData[index] = text;
        else
            fieldData[index] = fieldData[index] + '\n' + text;
        }

    private int putMolText(int entry, StringBuffer text, int mol, DTP dtp) {
        int[] data = new int[BUFFER_SIZE];

        if (dtp.isparent == 1)
            return kErrGetMolInfoNoParent;
        
        int blocks = 0;
        try {
            blocks = getData(entry, data, dtp);
            }
        catch (IOException e) {}

        if (blocks == 0)
            return kErrGetMolInfoNoData;
        
        if (dtp.typno == 0) {
            int offset = 0;
            for (int block=0; block<blocks; block++) {
                for (int count=0; count<dtp.sbfnum; count++) {
                    String text1 = null;
                    String text2 = null;
                    int sbf = dtp.sbfpoin + count - 1;
                    if (dtp.length == 0)
                        if (mSBFDir[sbf].begin > data[offset])
                            break;

                    text1 = mSBFDir[sbf].name;
                    int datapoin = offset+mSBFDir[sbf].begin-(dtp.length == 0 ? 0 : 1);

                    if (mSBFDir[sbf].type == 1) {      // float-float range
                        if (mSBFDir[sbf].format2.length() == 0 || data[datapoin] == 0x20202020) {
                            text2 = "";
                            continue;
                            }
                        text2 = filterText(formatedString(data, datapoin, mSBFDir[sbf]));
                        }
                    else if (mSBFDir[sbf].type == 2) { // fixed length text
                        if (data[datapoin] == 0x80808080) {
                            text2 = "";
                            continue;
                            }
                        StringBuffer buf = new StringBuffer();
                        int v = 0;
                        for (int i=0; i<mSBFDir[sbf].datalen; i++) {
                            v = ((i & 3) == 0) ? data[datapoin+(i >> 2)] : v >>> 8;
                            buf.append((char)(v & 0x000000FF));
                            }
                        text2 = filterText(buf.toString());
                        }
                    else if (mSBFDir[sbf].type == 4) {  // integer
                        if (data[datapoin] == 0x20202020) {
                            text2 = "";
                            continue;
                            }
                        text2 = ""+data[datapoin];
                        }
                    else if (mSBFDir[sbf].type == 5) {  // variable length text
                        if (data[datapoin] == 0 || data[datapoin] == 0x80808080) {
                            text2 = "";
                            continue;
                            }
                        int length = 4*(data[offset]+1-mSBFDir[sbf].begin);
                        StringBuffer buf = new StringBuffer();
                        int v = 0;
                        for (int i=0; i<length; i++) {
                            v = ((i & 3) == 0) ? data[datapoin+(i >> 2)] : v >>> 8;
                            buf.append((char)(v & 0x000000FF));
                            }
                        text2 = filterText(buf.toString());
                        }
                    if (text2 != null) {
                        if (text.length() != 0)
                            text.append('\n');
                        text.append(""+(mol+1)+") "+text1);
                        if (!text1.endsWith(":"))
                            text.append(":");
                        text.append(text2);
                        }
                    }
                offset += (dtp.length != 0) ? dtp.length+1 : data[offset]+2;
                }
            }
        
        return kErrNoError;
        }
        
    private String formatedString(int[] data, int datapoin, SBF sbf) {
        double[] range = new double[2];
        StringBuffer string = new StringBuffer();
        int dataCount = 0;
        int formatpoin = 0;
        int lengthAfterR1 = 0;
        while (formatpoin < sbf.format2.length()) {
            if (sbf.format2.charAt(formatpoin) == 'R') {
                if (dataCount > 1)
                    return string.toString();
                if (dataCount == 1) {
                    char previous = string.charAt(string.length()-1);
                    if (previous >= '0' && previous <= 9)
                        string.append(' ');
                    }
                range[dataCount] = convertFloat(data[datapoin]);
                if (dataCount != 0) {   // second float value
                    if (range[0] == range[1]) {
                        string.setLength(lengthAfterR1);
                        formatpoin++;
                        continue;
                        }
                    }
                string.append(DoubleFormat.toString(1.00000001*range[dataCount]));
                if (dataCount == 0)
                    lengthAfterR1 = string.length();
                dataCount++;
                datapoin++;
                }
        
            if (sbf.format2.charAt(formatpoin) == '\'') {
                formatpoin ++;
                while (sbf.format2.charAt(formatpoin) != '\'' && (formatpoin < 20)) {
                    if (sbf.format2.charAt(formatpoin) == '-')
                        string.append(" - ");
                    else
                        string.append(sbf.format2.charAt(formatpoin));
                    formatpoin++;
                    }
                }
        
            formatpoin++;
            }
        return string.toString();
        }

    private String filterText(String s) {
        return s;
        }
    
    private void getDeepCatalysts(int entry, DTP dtp) throws IOException {
        //get info about catalysts/solvents if datatypes are two levels down VARIATION

        int[] data = new int[50];
        int blocks = getData(entry, data, mDTPDir[dtp.rootID[1]-1]);

        for (int block=0; block<blocks; block++)
            getCatalysts(data[block*2], dtp);
        }

    private void getCatalysts(int entry, DTP dtp) throws IOException {
        int[] data = new int[50];
        int[] regno = new int[4];
        
        int blocks = getData(entry, data, dtp);      // no molecules found
        if (blocks == 0)
            return;

        for (int block=0; block<blocks; block++) {
            for (int count=0; count<20; count++) {
                int deepdtp = dtp.depdata[count]-1;
                if (deepdtp == -1)
                    break;

                try {
                    if (mDTPDir[deepdtp].typno == -5
                     && getData(data[block*2], regno, mDTPDir[deepdtp]) == 1) {
                        mSolvents.add(getMolecule(regno[0]));
                        mSolventRegNo[mSolventCount] = regno[0];
                        mSolventCount++;
                        }
    
                    if (mDTPDir[deepdtp].typno == -6
                      && getData(data[block*2], regno, mDTPDir[deepdtp]) == 1) {
                        mCatalysts.add(getMolecule(regno[0]));
                        mCatalystRegNo[mCatalystCount] = regno[0];
                        mCatalystCount++;
                        }
                    }
                catch (IOException e) {}    // ignore empty SEMA data etc. of catalysts and solvents

                break;
                }
            }
        }

    private int getData(int entry, int[] data, DTP dtp) throws IOException {
        int pointer = getPointer(entry, dtp);           // get pointer into rxn/mol
        if (pointer == 0)
            return 0;

        if (pointer < 0)
            throw new IOException("getData() pointer < 0");

        String filename = datafile(dtp.drpoin);
        DataInputStream dis = getDataInputStream(filename);
        int size = readFileSize(dis);
        if (4+4*pointer >= size)
            throw new IOException("pointer >= filesize");
        dis.skipBytes(4*pointer);

        int offset = 0;
        int blocks = 0;
        switch (dtp.length) {
        case 0:                         // typ: n,data,[x,n,data ...],0
            do {
                data[offset] = readInt(dis);
                if (data[offset] < 0 || data[offset] >= BUFFER_SIZE-offset-2)
                    throw new IOException("getData() unexpected value");
                for (int i=0; i<=data[offset]; i++)
                    data[offset+i+1] = readInt(dis);
                offset += data[offset]+2;
                blocks++;
                } while ((dtp.access2 == 'M') && (data[offset-1] == pointer+offset));
            break;
        default:                        // typ: fixed length,[x,fixed length, ...],0
            do {
                if ((dtp.length < 0) || (offset+dtp.length > BUFFER_SIZE-2))
                    throw new IOException("getData() unexpected value");
                for (int i=0; i<=dtp.length; i++)
                    data[offset+i] = readInt(dis);
                offset += dtp.length+1;
                blocks++;
                } while ((dtp.access2 == 'M') && (data[offset-1] == pointer+offset));
            break;
            }

        dis.close();
    
        return blocks;
        }

    private void getReaction(int entry, DTP dtp) throws IOException {
        int[] data = new int[20];

        if (getData(entry, data, dtp) != 1)
            throw new IOException("getReaction() no molecules");

        mReaction = new Reaction();
        for (int i=1; i<=data[0]; i++) {
            mMolRegNo[i-1] = Math.abs(data[i]);
            if (data[i] < 0)
                mReaction.addReactant(getMolecule(-data[i]));
            else
                mReaction.addProduct(getMolecule(data[i]));
            }
        }

    private StereoMolecule getMolecule(int regNo) throws IOException {
        StereoMolecule mol = new StereoMolecule();

        for(int i=0; i<mDTPDir.length; i++) {
            DTP dtp = mDTPDir[i];
            if (dtp.parentID != -1)
                continue;      // skip reaction datatypes

            if (dtp.typno == 1)           // SEMA
                getSema(mol, regNo, dtp);
            else if (dtp.typno == 2)      // COORDS
                getCoords(mol, regNo, dtp);
            }

        new AromaticityResolver(mol).locateDelocalizedDoubleBonds();
        mol.setStereoBondsFromParity();

        return mol;
        }

    private void getSema(Molecule mol, int molRegNo, DTP dtp) throws IOException {
        if (getData(molRegNo, mBuffer, dtp) != 1)
            throw new IOException("getSema() no sema data");
        if (mBuffer[1] == -1)
            throw new IOException("getSema() empty sema data");
    
        mBufferIndex = 2;
        mBitmask = 0x80000000;
    
        int entryLen = 1 + readBits(4);

        mol.setAllAtoms(readBits(entryLen));
        mol.setAllBonds(readBits(entryLen));
    
        if (mol.getAllAtoms() > mol.getMaxAtoms()
         || mol.getAllBonds() > mol.getMaxBonds())
            throw new IOException("getSema() max atoms or bonds exceeded");
    
        int fragments = readBits(entryLen);
    
        if (mol.getAllBonds() < mol.getAllAtoms()-fragments)
            throw new IOException("getSema() unexpected few bonds");
    
        int bnd = 0;
        for (int i=0; i<mol.getAllAtoms()-1; i++) {
            int atom1 = readBits(entryLen ) - 1;
            if (atom1 == -1)
                continue;

            int atom2 = i+1;
            mol.setBondAtom(0, bnd, atom1);
            mol.setBondAtom(1, bnd, atom2);
            bnd++;
            }
        for (int i=mol.getAllAtoms()-1; i<mol.getAllBonds(); i++) {
            int atom1 = readBits(entryLen) - 1;
            if (atom1 == -1)
                continue;

            int atom2 = readBits(entryLen) - 1;
            mol.setBondAtom(0, bnd, atom1);
            mol.setBondAtom(1, bnd, atom2);
            bnd++;
            }

        for (int atm=0; atm<mol.getAllAtoms(); atm++)  // atomic numbers
            mol.setAtomicNo(atm, readBits(8));
    
        int valences = readBits(entryLen);       // valence information
        for (int cAtm=0; cAtm<valences; cAtm++)
            readBits(entryLen+4);

        int isotops = readBits(entryLen);        // isotop information
        for (int cAtm=0; cAtm<isotops; cAtm++) {
            int atm = readBits(entryLen) - 1;
            int mass = readBits(5);
            mol.setAtomMass(atm, mass - 18);
            }
    
        int charged = readBits(entryLen);        // charge information
        for (int cAtm=0; cAtm<charged; cAtm++) {
            int atm = readBits(entryLen) - 1;
            mol.setAtomCharge(atm, 16 - readBits(5));
            }
    
        int radicals = readBits(entryLen);           // radical information
        for (int cAtm=0; cAtm<radicals; cAtm++) {
            int atm = readBits(entryLen) - 1;
            mol.setAtomRadical(atm, readBits(2));
            }
    
        bnd = 0;
        for (int i=0; i<mol.getAllBonds(); i++) {
            int order = readBits(3);            // bit 2 set -> bond in ring
            if (order == 0)
                continue;

            order &= 3;
            mol.setBondType(bnd, order == 0 ? Molecule.cBondTypeDelocalized :
                                 order == 1 ? Molecule.cBondTypeSingle :
                                 order == 2 ? Molecule.cBondTypeDouble
                                            : Molecule.cBondTypeTriple);
            bnd++;
            }
        mol.setAllBonds(bnd);
    
        int unknowns = readBits(entryLen);   // trash E/Z bond info
        for (int i=0; i<unknowns; i++) readBits(3);
    
        unknowns = readBits(2);
        if ((unknowns & 2) != 0) {
            for (int atm=0; atm<mol.getAllAtoms(); atm++) {
                int parity = readBits(3);        // stereo parity
                if (parity == 4 || parity == 5)
                    mol.setAtomParity(atm, parity - 3, false);
                }
            }
        }

    private void getCoords(Molecule mol, int molRegNo, DTP dtp) throws IOException {
        if (getData(molRegNo, mBuffer, dtp) != 1)
            throw new IOException("getCoords() no coordinates");

        mBufferIndex = 1;
        mBitmask = 0x80000000;
        for (int atm=0; atm<mol.getAllAtoms(); atm++) {
            mol.setAtomY(atm, -(short)readBits(16));
            mol.setAtomX(atm, (short)readBits(16));
            }
        }

    private void getMapping(int entry, DTP dtp) throws IOException {
        if (getData(entry, mBuffer, dtp) != 1)
            throw new IOException("getMapping() no mapping");

        mBitmask = 0x80000000;
        mBufferIndex = 1;
        int datalen1 = (1 + readBits(8)) >> 1;
        int datalen2 = (1 + readBits(8)) >> 1;
        readBits(12);
        int entryLen = readBits(4);

        mBufferIndex = 2+datalen1;
        int[] atms = new int[mReaction.getMolecules()];
        for (int mol=0; mol<mReaction.getMolecules(); mol++)
            atms[mol] = readBits(8);

        if ((mReaction.getMolecules() % 4) != 0) {
            mBufferIndex++;
            mBitmask = 0x80000000;
            }
        
        mBufferIndex += datalen2;

        for (int mol=0; mol<mReaction.getMolecules(); mol++)
            for (int atm=0; atm<atms[mol]; atm++)
                mReaction.getMolecule(mol).setAtomMapNo(atm, readBits(entryLen), false);
        }

    private int getPointer(int entry, DTP dtp) throws IOException {
        if (entry == 0x80808080)
            return 0;
        DataInputStream dis = getDataInputStream(pointerfile(dtp.drpoin));
        dis.skipBytes(4+(1+entry)*dtp.drsize*4);
        try {
            int pointer = readInt(dis);
            dis.close();
            return pointer;
            } catch (IOException e) {
                System.out.println("getPointer(entry:"+Integer.toHexString(entry)+","+dtp.lnum+") skip:"+(4+(1+entry)*dtp.drsize*4)); 
//              e.printStackTrace();
                return 0;
                }
        }

    private int readFileSize(DataInputStream dis) throws IOException {
        int size = readInt(dis);
        return (size < 0) ? -size            // REACCS format
                          : dis.available(); // ISIS format
        }

    private int readInt(DataInputStream dis) throws IOException {
        return invertInt(dis.readInt());
        }

    private int invertInt(int i) {
        return ((i & 0x000000FF) << 24)
             + ((i & 0x0000FF00) << 8)
             + ((i & 0x00FF0000) >>> 8)
             + ((i & 0xFF000000) >>> 24);
        }

    private void extractFloatYield() {
        if (mBuffer[0] != 0x20202020) {
            double yield1 = convertFloat(mBuffer[0]);
            double yield2 = convertFloat(mBuffer[1]);
            if (yield1 >= 0 && yield1 <= 100.1) {
                if (yield2 >= 0 && yield2 <= 100.1)
                    yield1 = (yield1 + yield2) / 2;
                mYield = (int)(yield1 + 0.5);
                }
            }
        }

    private void extractIntYield() {
        if (mBuffer[0] >= 0 && mBuffer[0] <= 100)
            mYield = mBuffer[0];
        }

    private double convertFloat(int i) {      // changes VAX float to Java double
        if (i == 0) return 0;

        int e = (i & 0x00007F80) >> 7;
        int m = ((i & 0x0000007F) << 16) | ((i & 0xFFFF0000) >>> 16) | 0x00800000;
        double v = (double)m/(double)0x01000000 * Math.pow(2, e-128);
        return ((i & 0x00008000) == 0) ? v : -v;
        }

    private int readBits(int count) {
        int retval = 0;
        for(int i=0; i<count; i++) {
            retval <<= 1;
            if ((mBitmask & mBuffer[mBufferIndex]) != 0)
                retval |= 1;
            mBitmask >>>= 1;
            if (mBitmask == 0) {
                mBitmask = 0x80000000;
                mBufferIndex++;
                }
            }
        return retval;
        }

    private DataInputStream getDataInputStream(String filename) throws IOException {
        if (new File(mDirectory+filename).exists())
            return new DataInputStream(new FileInputStream(mDirectory+filename));
            
        return new DataInputStream(new FileInputStream(mDirectory+filename.toLowerCase()));
        }
    }

class DTP {
    static final int SIZE = 188;
    int lnum;
    int drpoin;
    int parentID;
    int length;
    int typno;
    int security;
    int sbfpoin;
    int sbfnum;
    int hash;
    int unk5;
    int unk6;
    int unk7;
    String dtpnam;  // 20 bytes
    byte access1;
    byte access2;
    byte[] empty;   // 2 bytes
    int isparent;
    int[] depdata;  // 20 ints
    int[] rootID;   // 4 ints
    int unk9;
    int unk10;
    int drsize;
    int unk11;
    }

class SBF {
    static final int SIZE = 160;
    String shortnam;    // 20 bytes
    String format1;     // 20 bytes
    String format2;     // 20 bytes
    String name;        // 20 bytes
    int lnum;
    int type;
    int a2;
    int a3;
    int datalen;
    int datatyp;
    int a5;
    int dtppoin;
    int a7;
    int begin;
    int a8;
    int a9;
    int a10;
    int a11;
    int a12;
    int a13;
    int a14;
    int a15;
    int a16;
    int a17;
    int fieldNo;
    }
