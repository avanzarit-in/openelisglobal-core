/*
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is OpenELIS code.
 *
 * Copyright (C) ITECH, University of Washington, Seattle WA.  All Rights Reserved.
 */

package us.mn.state.health.lims.testconfiguration.action;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.validator.DynaValidatorForm;
import org.hibernate.HibernateException;
import org.hibernate.Transaction;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import us.mn.state.health.lims.common.action.BaseAction;
import us.mn.state.health.lims.common.services.*;
import us.mn.state.health.lims.hibernate.HibernateUtil;
import us.mn.state.health.lims.localization.dao.LocalizationDAO;
import us.mn.state.health.lims.localization.daoimpl.LocalizationDAOImpl;
import us.mn.state.health.lims.localization.valueholder.Localization;
import us.mn.state.health.lims.panel.daoimpl.PanelDAOImpl;
import us.mn.state.health.lims.panelitem.dao.PanelItemDAO;
import us.mn.state.health.lims.panelitem.daoimpl.PanelItemDAOImpl;
import us.mn.state.health.lims.panelitem.valueholder.PanelItem;
import us.mn.state.health.lims.test.dao.TestDAO;
import us.mn.state.health.lims.test.daoimpl.TestDAOImpl;
import us.mn.state.health.lims.test.valueholder.Test;
import us.mn.state.health.lims.test.valueholder.TestSection;
import us.mn.state.health.lims.testresult.dao.TestResultDAO;
import us.mn.state.health.lims.testresult.daoimpl.TestResultDAOImpl;
import us.mn.state.health.lims.testresult.valueholder.TestResult;
import us.mn.state.health.lims.typeofsample.dao.TypeOfSampleDAO;
import us.mn.state.health.lims.typeofsample.dao.TypeOfSampleTestDAO;
import us.mn.state.health.lims.typeofsample.daoimpl.TypeOfSampleDAOImpl;
import us.mn.state.health.lims.typeofsample.daoimpl.TypeOfSampleTestDAOImpl;
import us.mn.state.health.lims.typeofsample.util.TypeOfSampleUtil;
import us.mn.state.health.lims.typeofsample.valueholder.TypeOfSample;
import us.mn.state.health.lims.typeofsample.valueholder.TypeOfSampleTest;
import us.mn.state.health.lims.unitofmeasure.daoimpl.UnitOfMeasureDAOImpl;
import us.mn.state.health.lims.unitofmeasure.valueholder.UnitOfMeasure;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestAddUpdate extends BaseAction {
    private TypeOfSampleDAO typeOfSampleDAO = new TypeOfSampleDAOImpl();
    @Override
    protected ActionForward performAction(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String currentUserId = getSysUserId(request);
        String jsonString = ((DynaValidatorForm)form).getString("jsonWad");
        System.out.println(jsonString);

        JSONParser parser=new JSONParser();

        JSONObject obj = (JSONObject)parser.parse(((DynaValidatorForm)form).getString("jsonWad"));
        TestAddParams testAddParams = extractTestAddParms(obj, parser);
        List<TestSet> testSets = createTestSets(testAddParams);
        Localization nameLocalization = createNameLocalization(testAddParams);
        Localization reportingNameLocalization = createReportingNameLocalization(testAddParams);

        LocalizationDAO localizationDAO = new LocalizationDAOImpl();
        TestDAO testDAO = new TestDAOImpl();
        PanelItemDAO panelItemDAO = new PanelItemDAOImpl();
        TestResultDAO testResultDAO = new TestResultDAOImpl();
        TypeOfSampleTestDAO typeOfSampleTestDAO = new TypeOfSampleTestDAOImpl();

        Transaction tx = HibernateUtil.getSession().beginTransaction();
        try{

            nameLocalization.setSysUserId(currentUserId);
            localizationDAO.insert(nameLocalization);
            reportingNameLocalization.setSysUserId(currentUserId);
            localizationDAO.insert(reportingNameLocalization);

            for( TestSet set : testSets){
                set.test.setSysUserId(currentUserId);
                set.test.setLocalizedTestName(nameLocalization);
                set.test.setLocalizedReportingName(reportingNameLocalization);
                testDAO.insertData(set.test);

                for( Test test :set.sortedTests){
                    test.setSysUserId(currentUserId);
                    testDAO.updateData(test);
                }

                set.sampleTypeTest.setSysUserId(currentUserId);
                set.sampleTypeTest.setTestId(set.test.getId());
                typeOfSampleTestDAO.insertData(set.sampleTypeTest);

                for( PanelItem item : set.panelItems){
                    item.setSysUserId(currentUserId);
                    item.setTest(set.test);
                    panelItemDAO.insertData(item);
                }

                for( TestResult testResult : set.testResults){
                    testResult.setSysUserId(currentUserId);
                    testResult.setTest(set.test);
                    testResultDAO.insertData(testResult);
                }
            }

            tx.commit();
        }catch( HibernateException e ){
            tx.rollback();
        }finally{
            HibernateUtil.closeSession();
        }

        TestService.refreshTestNames();
        TypeOfSampleUtil.clearCache();
        return mapping.findForward(FWD_SUCCESS);
    }

    private void createPanelItems(ArrayList<PanelItem> panelItems, TestAddParams testAddParams) {
        PanelDAOImpl panelDAO = new PanelDAOImpl();
        for( String panelId : testAddParams.panelList) {
            PanelItem panelItem = new PanelItem();
            panelItem.setPanel(panelDAO.getPanelById(panelId));
            panelItems.add(panelItem);
        }
    }

    private void createTestResults(ArrayList<TestResult> testResults, TestAddParams testAddParams) {
        TypeOfTestResultService.ResultType type = TypeOfTestResultService.getResultTypeById(testAddParams.resultTypeId);

        if (TypeOfTestResultService.ResultType.isTextOnlyVariant(type)){
            TestResult testResult = new TestResult();
            testResult.setTestResultType(type.getCharacterValue());
            testResult.setSortOrder("1");
            testResult.setIsActive(true);
            testResults.add(testResult);
        }
    }
    private Localization createNameLocalization(TestAddParams testAddParams) {
        return LocalizationService.createNewLocalization(testAddParams.testNameEnglish,
                testAddParams.testNameFrench, LocalizationService.LocalizationType.TEST_NAME);
    }

    private Localization createReportingNameLocalization(TestAddParams testAddParams) {
        return  LocalizationService.createNewLocalization(testAddParams.testReportNameEnglish,
                testAddParams.testReportNameFrench, LocalizationService.LocalizationType.REPORTING_TEST_NAME);
    }

    private List<TestSet> createTestSets(TestAddParams testAddParams) {
        List<TestSet> testSets = new ArrayList<TestSet>();
        UnitOfMeasure uom = null;
        if(!GenericValidator.isBlankOrNull(testAddParams.uomId) || "0".equals(testAddParams.uomId)) {
            uom = new UnitOfMeasureDAOImpl().getUnitOfMeasureById(testAddParams.uomId);
        }
        TestSection testSection = new TestSectionService( testAddParams.testSectionId).getTestSection();
        //The number of test sets depend on the number of sampleTypes
        for( int i = 0; i < testAddParams.sampleList.size(); i++){
            TypeOfSample typeOfSample = typeOfSampleDAO.getTypeOfSampleById(testAddParams.sampleList.get(i).sampleTypeId);
            if (typeOfSample == null) {
                continue;
            }
            TestSet testSet = new TestSet();
            Test test = new Test();
            test.setUnitOfMeasure(uom);
            test.setDescription(testAddParams.testNameEnglish + "(" + typeOfSample.getDescription() + ")");
            test.setTestName(testAddParams.testNameEnglish);
            test.setLocalCode(testAddParams.testNameEnglish);
            test.setIsActive(testAddParams.active);
            test.setOrderable("Y".equals(testAddParams.orderable));
            test.setIsReportable("N");
            test.setTestSection(testSection);
            test.setGuid(String.valueOf(UUID.randomUUID()));
            ArrayList<String> orderedTests = testAddParams.sampleList.get(i).orderedTests;
            for( int j = 0; j < orderedTests.size(); j++){
                if( "0".equals(orderedTests.get(j))){
                    test.setSortOrder(String.valueOf(j));
                }else {
                    Test orderedTest = new TestService(orderedTests.get(j)).getTest();
                    orderedTest.setSortOrder(String.valueOf(j));
                    testSet.sortedTests.add(orderedTest);
                }
            }

            testSet.test = test;

            TypeOfSampleTest typeOfSampleTest = new TypeOfSampleTest();
            typeOfSampleTest.setTypeOfSampleId(typeOfSample.getId());
            testSet.sampleTypeTest = typeOfSampleTest;

            createPanelItems( testSet.panelItems, testAddParams);
            createTestResults( testSet.testResults, testAddParams );
            testSets.add( testSet);
        }

        return testSets;
    }


    private TestAddParams extractTestAddParms(JSONObject obj, JSONParser parser) {
        TestAddParams testAddParams = new TestAddParams();
        try {

            testAddParams.testNameEnglish = (String) obj.get("testNameEnglish");
            testAddParams.testNameFrench = (String) obj.get("testNameFrench");
            testAddParams.testReportNameEnglish = (String) obj.get("testReportNameEnglish");
            testAddParams.testReportNameFrench = (String) obj.get("testReportNameFrench");
            testAddParams.testSectionId = (String) obj.get("testSection");
            extractPanels(obj, parser, testAddParams);
            testAddParams.uomId = (String)obj.get("uom");
            testAddParams.resultTypeId = (String)obj.get("resultType");
            extractSampleTypes(obj, parser, testAddParams);
            testAddParams.active = (String) obj.get("active");
            testAddParams.orderable = (String) obj.get("orderable");

        } catch (ParseException e) {
            e.printStackTrace();
        }

        return testAddParams;
    }

    private void extractPanels(JSONObject obj, JSONParser parser, TestAddParams testAddParams) throws ParseException {
        String panels = (String)obj.get("panels");
        JSONArray panelArray = (JSONArray) parser.parse(panels);

        for (int i = 0; i < panelArray.size(); i++) {
            testAddParams.panelList.add((String) (((JSONObject) panelArray.get(i)).get("id")));
        }

    }

    private void extractSampleTypes(JSONObject obj, JSONParser parser, TestAddParams testAddParams) throws ParseException {
        String sampleTypes = (String)obj.get("sampleTypes");
        JSONArray sampleTypeArray = (JSONArray) parser.parse(sampleTypes);

        for (int i = 0; i < sampleTypeArray.size(); i++) {
            SampleTypeListAndTestOrder sampleTypeTests = new SampleTypeListAndTestOrder();
            sampleTypeTests.sampleTypeId = (String) (((JSONObject) sampleTypeArray.get(i)).get("typeId"));

            JSONArray testArray = (JSONArray) (((JSONObject)sampleTypeArray.get(i)).get("tests"));
            for( int j = 0; j < testArray.size(); j++){
                sampleTypeTests.orderedTests.add( String.valueOf(((JSONObject) testArray.get(j)).get("id")));
            }
            testAddParams.sampleList.add(sampleTypeTests);
        }
    }

    @Override
    protected String getPageTitleKey() {
        return null;
    }

    @Override
    protected String getPageSubtitleKey() {
        return null;
    }

    private class TestAddParams{
        String testNameEnglish;
        String testNameFrench;
        String testReportNameEnglish;
        String testReportNameFrench;
        String testSectionId;
        ArrayList<String> panelList = new ArrayList<String>();
        String uomId;
        String resultTypeId;
        ArrayList<SampleTypeListAndTestOrder> sampleList = new ArrayList<SampleTypeListAndTestOrder>();
        String active;
        String orderable;


    }

    private class SampleTypeListAndTestOrder{
        String sampleTypeId;
        ArrayList<String> orderedTests = new ArrayList<String>();
    }

    private class TestSet{
        Test test;
        TypeOfSampleTest sampleTypeTest;
        ArrayList<Test> sortedTests = new ArrayList<Test>();
        ArrayList<PanelItem> panelItems = new ArrayList<PanelItem>();
        ArrayList<TestResult> testResults = new ArrayList<TestResult>();
    }
}