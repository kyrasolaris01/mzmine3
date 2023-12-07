package io.github.mzmine.modules.dataprocessing.id_lipididentification.common.lipididentificationtools.lipidfragmentannotation;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.MassList;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.impl.SimpleDataPoint;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.common.lipididentificationtools.LipidFragmentationRule;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.common.lipididentificationtools.LipidFragmentationRuleType;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.common.lipids.ILipidAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.common.lipids.LipidFragment;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.common.lipids.lipidchain.ILipidChain;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.common.lipids.lipidchain.LipidChainType;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.common.lipidutils.LipidChainFactory;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidannotationmodules.LipidAnnotationChainParameters;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.FormulaUtils;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

public abstract class AbstractLipidFragmentFactory {

  protected static final LipidChainFactory LIPID_CHAIN_FACTORY = new LipidChainFactory();


  protected MZTolerance mzToleranceMS2;
  protected ILipidAnnotation lipidAnnotation;
  protected IonizationType ionizationType;
  protected LipidFragmentationRule[] rules;
  protected Scan msMsScan;

  protected final int minChainLength;
  protected final int maxChainLength;
  protected final int maxDoubleBonds;
  protected final int minDoubleBonds;
  protected final Boolean onlySearchForEvenChains;

  public AbstractLipidFragmentFactory(MZTolerance mzToleranceMS2, ILipidAnnotation lipidAnnotation,
      IonizationType ionizationType, LipidFragmentationRule[] rules, Scan msMsScan,
      LipidAnnotationChainParameters chainParameters) {
    this.mzToleranceMS2 = mzToleranceMS2;
    this.lipidAnnotation = lipidAnnotation;
    this.ionizationType = ionizationType;
    this.rules = rules;
    this.msMsScan = msMsScan;
    this.minChainLength = chainParameters.getParameter(
        LipidAnnotationChainParameters.minChainLength).getValue();
    this.maxChainLength = chainParameters.getParameter(
        LipidAnnotationChainParameters.maxChainLength).getValue();
    this.minDoubleBonds = chainParameters.getParameter(LipidAnnotationChainParameters.minDBEs)
        .getValue();
    this.maxDoubleBonds = chainParameters.getParameter(LipidAnnotationChainParameters.maxDBEs)
        .getValue();
    this.onlySearchForEvenChains = chainParameters.getParameter(
        LipidAnnotationChainParameters.onlySearchForEvenChainLength).getValue();
  }

  public List<LipidFragment> findCommonLipidFragments() {
    List<LipidFragment> lipidFragments = new ArrayList<>();
    for (LipidFragmentationRule rule : rules) {
      if (!ionizationType.equals(rule.getIonizationType())
          || rule.getLipidFragmentationRuleType() == null) {
        continue;
      }
      List<LipidFragment> detectedFragments = checkForCommonRuleTypes(rule);
      if (detectedFragments != null) {
        lipidFragments.addAll(detectedFragments);
      }
    }
    return lipidFragments;
  }

  private List<LipidFragment> checkForCommonRuleTypes(LipidFragmentationRule rule) {
    LipidFragmentationRuleType ruleType = rule.getLipidFragmentationRuleType();
    return switch (ruleType) {
      case HEADGROUP_FRAGMENT -> checkForHeadgroupFragment(rule, lipidAnnotation, msMsScan);
      case HEADGROUP_FRAGMENT_NL -> checkForHeadgroupFragmentNL(rule, lipidAnnotation, msMsScan);
      case PRECURSOR -> checkForOnlyPrecursor(rule, lipidAnnotation, msMsScan);
      case ACYLCHAIN_FRAGMENT -> checkForAcylChainFragment(rule, lipidAnnotation, msMsScan);
      case ACYLCHAIN_FRAGMENT_NL -> checkForAcylChainFragmentNL(rule, lipidAnnotation, msMsScan);
      case ACYLCHAIN_MINUS_FORMULA_FRAGMENT ->
          checkForAcylChainMinusFormulaFragment(rule, lipidAnnotation, msMsScan);
      case ACYLCHAIN_MINUS_FORMULA_FRAGMENT_NL ->
          checkForAcylChainMinusFormulaFragmentNL(rule, lipidAnnotation, msMsScan);
      case ACYLCHAIN_PLUS_FORMULA_FRAGMENT ->
          checkForAcylChainPlusFormulaFragment(rule, lipidAnnotation, msMsScan);
      case ACYLCHAIN_PLUS_FORMULA_FRAGMENT_NL ->
          checkForAcylChainPlusFormulaFragmentNL(rule, lipidAnnotation, msMsScan);
      case TWO_ACYLCHAINS_PLUS_FORMULA_FRAGMENT ->
          checkForTwoAcylChainsPlusFormulaFragment(rule, lipidAnnotation, msMsScan);
      case ALKYLCHAIN_PLUS_FORMULA_FRAGMENT ->
          checkForAlkylChainPlusFormulaFragment(rule, lipidAnnotation, msMsScan);
      case AMID_CHAIN_FRAGMENT -> checkForAmidChainFragment(rule, lipidAnnotation, msMsScan);
      case AMID_CHAIN_PLUS_FORMULA_FRAGMENT ->
          checkForAmidChainPlusFormulaFragment(rule, lipidAnnotation, msMsScan);
      case AMID_CHAIN_MINUS_FORMULA_FRAGMENT ->
          checkForAmidChainMinusFormulaFragment(rule, lipidAnnotation, msMsScan);
      case AMID_MONO_HYDROXY_CHAIN_FRAGMENT ->
          checkForAmidMonoHydroxyChainFragment(rule, lipidAnnotation, msMsScan);
      case AMID_MONO_HYDROXY_CHAIN_PLUS_FORMULA_FRAGMENT ->
          checkForAmidMonoHydroxyChainPlusFormulaFragment(rule, lipidAnnotation, msMsScan);
      case AMID_MONO_HYDROXY_CHAIN_MINUS_FORMULA_FRAGMENT ->
          checkForAmidMonoHydroxyChainMinusFormulaFragment(rule, lipidAnnotation, msMsScan);
      case AMID_CHAIN_FRAGMENT_NL -> checkForAmidChainFragmentNL(rule, lipidAnnotation, msMsScan);
      case AMID_CHAIN_PLUS_FORMULA_FRAGMENT_NL ->
          checkForAmidChainPlusFormulaFragmentNL(rule, lipidAnnotation, msMsScan);
      case AMID_CHAIN_MINUS_FORMULA_FRAGMENT_NL ->
          checkForAmidChainMinusFormulaFragmentNL(rule, lipidAnnotation, msMsScan);
      default -> List.of();
    };
  }

  private List<LipidFragment> checkForOnlyPrecursor(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    IMolecularFormula lipidFormula = null;
    try {
      lipidFormula = (IMolecularFormula) lipidAnnotation.getMolecularFormula().clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
    rule.getIonizationType().ionizeFormula(lipidFormula);
    Double mzFragmentExact = FormulaUtils.calculateMzRatio(lipidFormula);
    MassList massList = msMsScan.getMassList();
    int index = massList.binarySearch(mzFragmentExact, true);
    boolean fragmentMatched = false;
    BestDataPoint bestDataPoint = getBestDataPoint(mzFragmentExact, massList, index,
        fragmentMatched);
    if (bestDataPoint.fragmentMatched()) {
      return List.of(new LipidFragment(rule.getLipidFragmentationRuleType(),
          rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
          mzFragmentExact, MolecularFormulaManipulator.getString(lipidFormula),
          new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
          lipidAnnotation.getLipidClass(), null, null, null, null, msMsScan));
    } else {
      return List.of();
    }
  }

  private List<LipidFragment> checkForHeadgroupFragment(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    String fragmentFormula = rule.getMolecularFormula();
    Double mzFragmentExact = FormulaUtils.calculateMzRatio(fragmentFormula);
    MassList massList = msMsScan.getMassList();
    int index = massList.binarySearch(mzFragmentExact, true);
    boolean fragmentMatched = false;
    BestDataPoint bestDataPoint = getBestDataPoint(mzFragmentExact, massList, index,
        fragmentMatched);
    if (bestDataPoint.fragmentMatched()) {
      return List.of(new LipidFragment(rule.getLipidFragmentationRuleType(),
          rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
          mzFragmentExact, fragmentFormula,
          new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
          lipidAnnotation.getLipidClass(), null, null, null, null, msMsScan));
    } else {
      return List.of();
    }
  }

  private List<LipidFragment> checkForHeadgroupFragmentNL(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    IMolecularFormula formulaNL = FormulaUtils.createMajorIsotopeMolFormula(
        rule.getMolecularFormula());
    IMolecularFormula lipidFormula = null;
    try {
      lipidFormula = (IMolecularFormula) lipidAnnotation.getMolecularFormula().clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
    rule.getIonizationType().ionizeFormula(lipidFormula);
    IMolecularFormula fragmentFormula = FormulaUtils.subtractFormula(lipidFormula, formulaNL);
    Double mzFragmentExact = FormulaUtils.calculateMzRatio(fragmentFormula);
    MassList massList = msMsScan.getMassList();
    int index = massList.binarySearch(mzFragmentExact, true);
    boolean fragmentMatched = false;
    BestDataPoint bestDataPoint = getBestDataPoint(mzFragmentExact, massList, index,
        fragmentMatched);
    if (bestDataPoint.fragmentMatched()) {
      return List.of(new LipidFragment(rule.getLipidFragmentationRuleType(),
          rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
          mzFragmentExact, MolecularFormulaManipulator.getString(fragmentFormula),
          new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
          lipidAnnotation.getLipidClass(), null, null, null, null, msMsScan));
    } else {
      return List.of();
    }
  }

  private List<LipidFragment> checkForAcylChainFragment(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    if (rule.getPolarityType().equals(PolarityType.NEGATIVE)) {
      List<ILipidChain> fattyAcylChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
          LipidChainType.ACYL_CHAIN, minChainLength, maxChainLength, minDoubleBonds, maxDoubleBonds,
          onlySearchForEvenChains);
      List<LipidFragment> matchedFragments = new ArrayList<>();
      for (ILipidChain lipidChain : fattyAcylChains) {
        IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
        IonizationType.NEGATIVE_HYDROGEN.ionizeFormula(lipidChainFormula);
        Double mzExact = FormulaUtils.calculateMzRatio(lipidChainFormula);
        MassList massList = msMsScan.getMassList();
        int index = massList.binarySearch(mzExact, true);
        boolean fragmentMatched = false;
        BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
        if (bestDataPoint.fragmentMatched()) {
          int chainLength = lipidChain.getNumberOfCarbons();
          int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
          matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
              rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
              mzExact, MolecularFormulaManipulator.getString(lipidChainFormula),
              new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
              lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
              lipidChain.getNumberOfOxygens(), LipidChainType.ACYL_CHAIN, msMsScan));
        }
      }
      return matchedFragments;
    }
    return null;
  }


  private List<LipidFragment> checkForAcylChainFragmentNL(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    List<ILipidChain> fattyAcylChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
        LipidChainType.ACYL_CHAIN, minChainLength, maxChainLength, minDoubleBonds, maxDoubleBonds,
        onlySearchForEvenChains);
    List<LipidFragment> matchedFragments = new ArrayList<>();
    for (ILipidChain lipidChain : fattyAcylChains) {
      IMolecularFormula lipidFormula = null;
      try {
        lipidFormula = (IMolecularFormula) lipidAnnotation.getMolecularFormula().clone();
      } catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
      rule.getIonizationType().ionizeFormula(lipidFormula);
      IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
      IMolecularFormula fragmentFormula = FormulaUtils.subtractFormula(lipidFormula,
          lipidChainFormula);
      Double mzExact = FormulaUtils.calculateMzRatio(fragmentFormula);
      MassList massList = msMsScan.getMassList();
      int index = massList.binarySearch(mzExact, true);
      boolean fragmentMatched = false;
      BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
      if (bestDataPoint.fragmentMatched()) {
        int chainLength = lipidChain.getNumberOfCarbons();
        int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
        matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
            rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
            mzExact, MolecularFormulaManipulator.getString(fragmentFormula),
            new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
            lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
            lipidChain.getNumberOfOxygens(), LipidChainType.ACYL_CHAIN, msMsScan));
      }
    }
    return matchedFragments;
  }

  private List<LipidFragment> checkForAcylChainMinusFormulaFragment(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    IMolecularFormula modificationFormula = FormulaUtils.createMajorIsotopeMolFormula(
        rule.getMolecularFormula());
    List<ILipidChain> fattyAcylChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
        LipidChainType.ACYL_CHAIN, minChainLength, maxChainLength, minDoubleBonds, maxDoubleBonds,
        onlySearchForEvenChains);
    List<LipidFragment> matchedFragments = new ArrayList<>();
    for (ILipidChain lipidChain : fattyAcylChains) {
      IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
      IMolecularFormula fragmentFormula = FormulaUtils.subtractFormula(lipidChainFormula,
          modificationFormula);
      IMolecularFormula ionizedFragmentFormula = ionizeFragmentBasedOnPolarity(fragmentFormula,
          rule.getPolarityType());
      Double mzExact = FormulaUtils.calculateMzRatio(ionizedFragmentFormula);
      MassList massList = msMsScan.getMassList();
      int index = massList.binarySearch(mzExact, true);
      boolean fragmentMatched = false;
      BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
      if (bestDataPoint.fragmentMatched()) {
        int chainLength = lipidChain.getNumberOfCarbons();
        int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
        matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
            rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
            mzExact, MolecularFormulaManipulator.getString(ionizedFragmentFormula),
            new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
            lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
            lipidChain.getNumberOfOxygens(), LipidChainType.ACYL_CHAIN, msMsScan));
      }
    }
    return matchedFragments;
  }

  private List<LipidFragment> checkForAcylChainMinusFormulaFragmentNL(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    IMolecularFormula modificationFormula = FormulaUtils.createMajorIsotopeMolFormula(
        rule.getMolecularFormula());
    List<ILipidChain> fattyAcylChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
        LipidChainType.ACYL_CHAIN, minChainLength, maxChainLength, minDoubleBonds, maxDoubleBonds,
        onlySearchForEvenChains);
    List<LipidFragment> matchedFragments = new ArrayList<>();
    for (ILipidChain lipidChain : fattyAcylChains) {
      IMolecularFormula lipidFormula = null;
      try {
        lipidFormula = (IMolecularFormula) lipidAnnotation.getMolecularFormula().clone();
      } catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
      rule.getIonizationType().ionizeFormula(lipidFormula);
      IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
      IMolecularFormula fragmentFormula = FormulaUtils.subtractFormula(lipidChainFormula,
          modificationFormula);
      IMolecularFormula lipidMinusFragmentFormula = FormulaUtils.subtractFormula(lipidFormula,
          fragmentFormula);
      Double mzExact = FormulaUtils.calculateMzRatio(lipidMinusFragmentFormula);
      MassList massList = msMsScan.getMassList();
      int index = massList.binarySearch(mzExact, true);
      boolean fragmentMatched = false;
      BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
      if (bestDataPoint.fragmentMatched()) {
        int chainLength = lipidChain.getNumberOfCarbons();
        int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
        matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
            rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
            mzExact, MolecularFormulaManipulator.getString(lipidMinusFragmentFormula),
            new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
            lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
            lipidChain.getNumberOfOxygens(), LipidChainType.ACYL_CHAIN, msMsScan));
      }
    }
    return matchedFragments;
  }


  private List<LipidFragment> checkForAcylChainPlusFormulaFragment(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    IMolecularFormula modificationFormula = FormulaUtils.createMajorIsotopeMolFormula(
        rule.getMolecularFormula());
    List<ILipidChain> fattyAcylChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
        LipidChainType.ACYL_CHAIN, minChainLength, maxChainLength, minDoubleBonds, maxDoubleBonds,
        onlySearchForEvenChains);
    List<LipidFragment> matchedFragments = new ArrayList<>();
    for (ILipidChain lipidChain : fattyAcylChains) {
      IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
      IMolecularFormula fragmentFormula = FormulaUtils.addFormula(lipidChainFormula,
          modificationFormula);
      IMolecularFormula ionizedFragmentFormula = ionizeFragmentBasedOnPolarity(fragmentFormula,
          rule.getPolarityType());
      Double mzExact = FormulaUtils.calculateMzRatio(ionizedFragmentFormula);
      MassList massList = msMsScan.getMassList();
      int index = massList.binarySearch(mzExact, true);
      boolean fragmentMatched = false;
      BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
      if (bestDataPoint.fragmentMatched()) {
        int chainLength = lipidChain.getNumberOfCarbons();
        int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
        matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
            rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
            mzExact, MolecularFormulaManipulator.getString(ionizedFragmentFormula),
            new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
            lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
            lipidChain.getNumberOfOxygens(), LipidChainType.ACYL_CHAIN, msMsScan));
      }
    }
    return matchedFragments;
  }

  private List<LipidFragment> checkForAcylChainPlusFormulaFragmentNL(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    IMolecularFormula modificationFormula = FormulaUtils.createMajorIsotopeMolFormula(
        rule.getMolecularFormula());
    List<LipidFragment> matchedFragments = new ArrayList<>();
    List<ILipidChain> fattyAcylChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
        LipidChainType.ACYL_CHAIN, minChainLength, maxChainLength, minDoubleBonds, maxDoubleBonds,
        onlySearchForEvenChains);
    for (ILipidChain lipidChain : fattyAcylChains) {
      IMolecularFormula lipidFormula = null;
      try {
        lipidFormula = (IMolecularFormula) lipidAnnotation.getMolecularFormula().clone();
      } catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
      rule.getIonizationType().ionizeFormula(lipidFormula);
      IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
      IMolecularFormula fragmentFormula = FormulaUtils.addFormula(lipidChainFormula,
          modificationFormula);
      IMolecularFormula lipidMinusFragmentFormula = FormulaUtils.subtractFormula(lipidFormula,
          fragmentFormula);
      Double mzExact = FormulaUtils.calculateMzRatio(lipidMinusFragmentFormula);
      MassList massList = msMsScan.getMassList();
      int index = massList.binarySearch(mzExact, true);
      boolean fragmentMatched = false;
      BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
      if (bestDataPoint.fragmentMatched()) {
        int chainLength = lipidChain.getNumberOfCarbons();
        int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
        matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
            rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
            mzExact, MolecularFormulaManipulator.getString(lipidMinusFragmentFormula),
            new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
            lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
            lipidChain.getNumberOfOxygens(), LipidChainType.ACYL_CHAIN, msMsScan));
      }
    }
    return matchedFragments;
  }

  private List<LipidFragment> checkForTwoAcylChainsPlusFormulaFragment(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    IMolecularFormula modificationFormula = FormulaUtils.createMajorIsotopeMolFormula(
        rule.getMolecularFormula());
    List<ILipidChain> fattyAcylChainsOne = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
        LipidChainType.ACYL_CHAIN, minChainLength, maxChainLength, minDoubleBonds, maxDoubleBonds,
        onlySearchForEvenChains);
    List<ILipidChain> fattyAcylChainsTwo = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
        LipidChainType.ACYL_CHAIN, minChainLength, maxChainLength, minDoubleBonds, maxDoubleBonds,
        onlySearchForEvenChains);
    List<LipidFragment> matchedFragments = new ArrayList<>();
    for (ILipidChain lipidChain1 : fattyAcylChainsOne) {
      IMolecularFormula lipidChainFormulaOne = lipidChain1.getChainMolecularFormula();
      for (ILipidChain lipidChain2 : fattyAcylChainsTwo) {
        IMolecularFormula lipidChainFormulaTwo = lipidChain2.getChainMolecularFormula();
        IMolecularFormula combinedChainsFormula = FormulaUtils.addFormula(lipidChainFormulaOne,
            lipidChainFormulaTwo);
        IMolecularFormula fragmentFormula = FormulaUtils.addFormula(combinedChainsFormula,
            modificationFormula);
        IMolecularFormula ionizedFragmentFormula = ionizeFragmentBasedOnPolarity(fragmentFormula,
            rule.getPolarityType());
        Double mzExact = FormulaUtils.calculateMzRatio(ionizedFragmentFormula);
        MassList massList = msMsScan.getMassList();
        int index = massList.binarySearch(mzExact, true);
        boolean fragmentMatched = false;
        BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
        if (bestDataPoint.fragmentMatched()) {
          matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
              rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
              mzExact, MolecularFormulaManipulator.getString(ionizedFragmentFormula),
              new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
              lipidAnnotation.getLipidClass(),
              lipidChain1.getNumberOfCarbons() + lipidChain2.getNumberOfCarbons(),
              lipidChain1.getNumberOfDBEs() + lipidChain2.getNumberOfDBEs(),
              lipidChain2.getNumberOfOxygens(), LipidChainType.TWO_ACYL_CHAINS_COMBINED, msMsScan));
        }
      }
    }
    return matchedFragments;
  }

  private List<LipidFragment> checkForAlkylChainPlusFormulaFragment(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    IMolecularFormula modificationFormula = FormulaUtils.createMajorIsotopeMolFormula(
        rule.getMolecularFormula());
    List<ILipidChain> alkylChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
        LipidChainType.ALKYL_CHAIN, minChainLength, maxChainLength, minDoubleBonds, maxDoubleBonds,
        onlySearchForEvenChains);
    List<LipidFragment> matchedFragments = new ArrayList<>();
    for (ILipidChain lipidChain : alkylChains) {
      IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
      IMolecularFormula fragmentFormula = FormulaUtils.addFormula(lipidChainFormula,
          modificationFormula);
      IMolecularFormula ionizedFragmentFormula = ionizeFragmentBasedOnPolarity(fragmentFormula,
          rule.getPolarityType());
      Double mzExact = FormulaUtils.calculateMzRatio(ionizedFragmentFormula);
      MassList massList = msMsScan.getMassList();
      int index = massList.binarySearch(mzExact, true);
      boolean fragmentMatched = false;
      BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
      if (bestDataPoint.fragmentMatched()) {
        int chainLength = lipidChain.getNumberOfCarbons();
        int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
        matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
            rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
            mzExact, MolecularFormulaManipulator.getString(ionizedFragmentFormula),
            new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
            lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
            lipidChain.getNumberOfOxygens(), LipidChainType.ALKYL_CHAIN, msMsScan));
      }
    }
    return matchedFragments;
  }

  private List<LipidFragment> checkForAmidChainFragment(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    if (rule.getPolarityType().equals(PolarityType.NEGATIVE)) {
      List<ILipidChain> fattyAcylChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
          LipidChainType.AMID_CHAIN, minChainLength, maxChainLength, minDoubleBonds, maxDoubleBonds,
          onlySearchForEvenChains);
      List<LipidFragment> matchedFragments = new ArrayList<>();
      for (ILipidChain lipidChain : fattyAcylChains) {
        IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
        IMolecularFormula ionizedFragmentFormula = ionizeFragmentBasedOnPolarity(lipidChainFormula,
            rule.getPolarityType());
        Double mzExact = FormulaUtils.calculateMzRatio(ionizedFragmentFormula);
        MassList massList = msMsScan.getMassList();
        int index = massList.binarySearch(mzExact, true);
        boolean fragmentMatched = false;
        BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
        if (bestDataPoint.fragmentMatched()) {
          int chainLength = lipidChain.getNumberOfCarbons();
          int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
          matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
              rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
              mzExact, MolecularFormulaManipulator.getString(ionizedFragmentFormula),
              new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
              lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
              lipidChain.getNumberOfOxygens(), LipidChainType.AMID_CHAIN, msMsScan));
        }
      }
      return matchedFragments;
    }
    return null;
  }

  private List<LipidFragment> checkForAmidChainPlusFormulaFragment(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    IMolecularFormula modificationFormula = FormulaUtils.createMajorIsotopeMolFormula(
        rule.getMolecularFormula());
    List<ILipidChain> amidChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
        LipidChainType.AMID_CHAIN, minChainLength, maxChainLength, minDoubleBonds, maxDoubleBonds,
        onlySearchForEvenChains);
    List<LipidFragment> matchedFragments = new ArrayList<>();
    for (ILipidChain lipidChain : amidChains) {
      IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
      IMolecularFormula fragmentFormula = FormulaUtils.addFormula(lipidChainFormula,
          modificationFormula);
      IMolecularFormula ionizedFragmentFormula = ionizeFragmentBasedOnPolarity(fragmentFormula,
          rule.getPolarityType());
      Double mzExact = FormulaUtils.calculateMzRatio(ionizedFragmentFormula);
      MassList massList = msMsScan.getMassList();
      int index = massList.binarySearch(mzExact, true);
      boolean fragmentMatched = false;
      BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
      if (bestDataPoint.fragmentMatched()) {
        int chainLength = lipidChain.getNumberOfCarbons();
        int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
        matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
            rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
            mzExact, MolecularFormulaManipulator.getString(ionizedFragmentFormula),
            new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
            lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
            lipidChain.getNumberOfOxygens(), LipidChainType.AMID_CHAIN, msMsScan));
      }
    }
    return matchedFragments;
  }

  private List<LipidFragment> checkForAmidChainMinusFormulaFragment(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    IMolecularFormula modificationFormula = FormulaUtils.createMajorIsotopeMolFormula(
        rule.getMolecularFormula());
    List<ILipidChain> amidChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
        LipidChainType.AMID_CHAIN, minChainLength, maxChainLength, minDoubleBonds, maxDoubleBonds,
        onlySearchForEvenChains);
    List<LipidFragment> matchedFragments = new ArrayList<>();
    for (ILipidChain lipidChain : amidChains) {
      IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
      IMolecularFormula fragmentFormula = FormulaUtils.subtractFormula(lipidChainFormula,
          modificationFormula);
      IMolecularFormula ionizedFragmentFormula = ionizeFragmentBasedOnPolarity(fragmentFormula,
          rule.getPolarityType());
      Double mzExact = FormulaUtils.calculateMzRatio(ionizedFragmentFormula);
      MassList massList = msMsScan.getMassList();
      int index = massList.binarySearch(mzExact, true);
      boolean fragmentMatched = false;
      BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
      if (bestDataPoint.fragmentMatched()) {
        int chainLength = lipidChain.getNumberOfCarbons();
        int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
        matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
            rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
            mzExact, MolecularFormulaManipulator.getString(ionizedFragmentFormula),
            new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
            lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
            lipidChain.getNumberOfOxygens(), LipidChainType.AMID_CHAIN, msMsScan));
      }
    }
    return matchedFragments;
  }

  private List<LipidFragment> checkForAmidMonoHydroxyChainFragment(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {

    if (rule.getPolarityType().equals(PolarityType.NEGATIVE)) {
      List<ILipidChain> fattyAcylChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
          LipidChainType.AMID_MONO_HYDROXY_CHAIN, minChainLength, maxChainLength, minDoubleBonds,
          maxDoubleBonds, onlySearchForEvenChains);
      List<LipidFragment> matchedFragments = new ArrayList<>();
      for (ILipidChain lipidChain : fattyAcylChains) {
        IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
        ionizeFragmentBasedOnPolarity(lipidChainFormula, rule.getPolarityType());
        Double mzExact = FormulaUtils.calculateMzRatio(lipidChainFormula);
        MassList massList = msMsScan.getMassList();
        int index = massList.binarySearch(mzExact, true);
        boolean fragmentMatched = false;
        BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
        if (bestDataPoint.fragmentMatched()) {
          int chainLength = lipidChain.getNumberOfCarbons();
          int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
          matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
              rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
              mzExact, MolecularFormulaManipulator.getString(lipidChainFormula),
              new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
              lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
              lipidChain.getNumberOfOxygens(), LipidChainType.AMID_MONO_HYDROXY_CHAIN, msMsScan));
        }
      }
      return matchedFragments;
    }
    return null;
  }

  private List<LipidFragment> checkForAmidMonoHydroxyChainPlusFormulaFragment(
      LipidFragmentationRule rule, ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    IMolecularFormula modificationFormula = FormulaUtils.createMajorIsotopeMolFormula(
        rule.getMolecularFormula());
    List<ILipidChain> amidChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
        LipidChainType.AMID_MONO_HYDROXY_CHAIN, minChainLength, maxChainLength, minDoubleBonds,
        maxDoubleBonds, onlySearchForEvenChains);
    List<LipidFragment> matchedFragments = new ArrayList<>();
    for (ILipidChain lipidChain : amidChains) {
      IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
      IMolecularFormula fragmentFormula = FormulaUtils.addFormula(lipidChainFormula,
          modificationFormula);
      IMolecularFormula ionizedFragmentFormula = ionizeFragmentBasedOnPolarity(fragmentFormula,
          rule.getPolarityType());
      Double mzExact = FormulaUtils.calculateMzRatio(ionizedFragmentFormula);
      MassList massList = msMsScan.getMassList();
      int index = massList.binarySearch(mzExact, true);
      boolean fragmentMatched = false;
      BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
      if (bestDataPoint.fragmentMatched()) {
        int chainLength = lipidChain.getNumberOfCarbons();
        int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
        matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
            rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
            mzExact, MolecularFormulaManipulator.getString(ionizedFragmentFormula),
            new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
            lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
            lipidChain.getNumberOfOxygens(), LipidChainType.AMID_MONO_HYDROXY_CHAIN, msMsScan));
      }
    }
    return matchedFragments;
  }

  private List<LipidFragment> checkForAmidMonoHydroxyChainMinusFormulaFragment(
      LipidFragmentationRule rule, ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    IMolecularFormula modificationFormula = FormulaUtils.createMajorIsotopeMolFormula(
        rule.getMolecularFormula());
    List<ILipidChain> amidChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
        LipidChainType.AMID_MONO_HYDROXY_CHAIN, minChainLength, maxChainLength, minDoubleBonds,
        maxDoubleBonds, onlySearchForEvenChains);
    List<LipidFragment> matchedFragments = new ArrayList<>();
    for (ILipidChain lipidChain : amidChains) {
      IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
      IMolecularFormula fragmentFormula = FormulaUtils.subtractFormula(lipidChainFormula,
          modificationFormula);
      IMolecularFormula ionizedFragmentFormula = ionizeFragmentBasedOnPolarity(fragmentFormula,
          rule.getPolarityType());
      Double mzExact = FormulaUtils.calculateMzRatio(ionizedFragmentFormula);
      MassList massList = msMsScan.getMassList();
      int index = massList.binarySearch(mzExact, true);
      boolean fragmentMatched = false;
      BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
      if (bestDataPoint.fragmentMatched()) {
        int chainLength = lipidChain.getNumberOfCarbons();
        int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
        matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
            rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
            mzExact, MolecularFormulaManipulator.getString(ionizedFragmentFormula),
            new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
            lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
            lipidChain.getNumberOfOxygens(), LipidChainType.AMID_MONO_HYDROXY_CHAIN, msMsScan));
      }
    }
    return matchedFragments;
  }

  private List<LipidFragment> checkForAmidChainFragmentNL(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    List<ILipidChain> amidChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
        LipidChainType.AMID_CHAIN, minChainLength, maxChainLength, minDoubleBonds, maxDoubleBonds,
        onlySearchForEvenChains);
    List<LipidFragment> matchedFragments = new ArrayList<>();
    for (ILipidChain lipidChain : amidChains) {
      IMolecularFormula lipidFormula = null;
      try {
        lipidFormula = (IMolecularFormula) lipidAnnotation.getMolecularFormula().clone();
      } catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
      rule.getIonizationType().ionizeFormula(lipidFormula);
      IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
      IMolecularFormula fragmentFormula = FormulaUtils.subtractFormula(lipidFormula,
          lipidChainFormula);
      Double mzExact = FormulaUtils.calculateMzRatio(fragmentFormula);
      MassList massList = msMsScan.getMassList();
      int index = massList.binarySearch(mzExact, true);
      boolean fragmentMatched = false;
      BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
      if (bestDataPoint.fragmentMatched()) {
        int chainLength = lipidChain.getNumberOfCarbons();
        int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
        matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
            rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
            mzExact, MolecularFormulaManipulator.getString(fragmentFormula),
            new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
            lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
            lipidChain.getNumberOfOxygens(), LipidChainType.AMID_CHAIN, msMsScan));
      }
    }
    return matchedFragments;
  }

  private List<LipidFragment> checkForAmidChainPlusFormulaFragmentNL(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    IMolecularFormula modificationFormula = FormulaUtils.createMajorIsotopeMolFormula(
        rule.getMolecularFormula());
    List<ILipidChain> amidChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
        LipidChainType.AMID_CHAIN, minChainLength, maxChainLength, minDoubleBonds, maxDoubleBonds,
        onlySearchForEvenChains);
    List<LipidFragment> matchedFragments = new ArrayList<>();
    for (ILipidChain lipidChain : amidChains) {
      IMolecularFormula lipidFormula = null;
      try {
        lipidFormula = (IMolecularFormula) lipidAnnotation.getMolecularFormula().clone();
      } catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
      rule.getIonizationType().ionizeFormula(lipidFormula);
      IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
      IMolecularFormula fragmentFormula = FormulaUtils.addFormula(lipidChainFormula,
          modificationFormula);
      IMolecularFormula lipidMinusFragmentFormula = FormulaUtils.subtractFormula(lipidFormula,
          fragmentFormula);
      Double mzExact = FormulaUtils.calculateMzRatio(lipidMinusFragmentFormula);
      MassList massList = msMsScan.getMassList();
      int index = massList.binarySearch(mzExact, true);
      boolean fragmentMatched = false;
      BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
      if (bestDataPoint.fragmentMatched()) {
        int chainLength = lipidChain.getNumberOfCarbons();
        int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
        matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
            rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
            mzExact, MolecularFormulaManipulator.getString(lipidMinusFragmentFormula),
            new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
            lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
            lipidChain.getNumberOfOxygens(), LipidChainType.AMID_CHAIN, msMsScan));
      }
    }
    return matchedFragments;
  }

  private List<LipidFragment> checkForAmidChainMinusFormulaFragmentNL(LipidFragmentationRule rule,
      ILipidAnnotation lipidAnnotation, Scan msMsScan) {
    IMolecularFormula modificationFormula = FormulaUtils.createMajorIsotopeMolFormula(
        rule.getMolecularFormula());
    List<ILipidChain> amidChains = LIPID_CHAIN_FACTORY.buildLipidChainsInRange(
        LipidChainType.AMID_CHAIN, minChainLength, maxChainLength, minDoubleBonds, maxDoubleBonds,
        onlySearchForEvenChains);
    List<LipidFragment> matchedFragments = new ArrayList<>();
    for (ILipidChain lipidChain : amidChains) {
      IMolecularFormula lipidFormula = null;
      try {
        lipidFormula = (IMolecularFormula) lipidAnnotation.getMolecularFormula().clone();
      } catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
      rule.getIonizationType().ionizeFormula(lipidFormula);
      IMolecularFormula lipidChainFormula = lipidChain.getChainMolecularFormula();
      IMolecularFormula fragmentFormula = FormulaUtils.subtractFormula(lipidChainFormula,
          modificationFormula);
      IMolecularFormula lipidMinusFragmentFormula = FormulaUtils.subtractFormula(lipidFormula,
          fragmentFormula);
      Double mzExact = FormulaUtils.calculateMzRatio(lipidMinusFragmentFormula);
      MassList massList = msMsScan.getMassList();
      int index = massList.binarySearch(mzExact, true);
      boolean fragmentMatched = false;
      BestDataPoint bestDataPoint = getBestDataPoint(mzExact, massList, index, fragmentMatched);
      if (bestDataPoint.fragmentMatched()) {
        int chainLength = lipidChain.getNumberOfCarbons();
        int numberOfDoubleBonds = lipidChain.getNumberOfDBEs();
        matchedFragments.add(new LipidFragment(rule.getLipidFragmentationRuleType(),
            rule.getLipidFragmentInformationLevelType(), rule.getLipidFragmentationRuleRating(),
            mzExact, MolecularFormulaManipulator.getString(lipidMinusFragmentFormula),
            new SimpleDataPoint(bestDataPoint.mzValue(), massList.getIntensityValue(index)),
            lipidAnnotation.getLipidClass(), chainLength, numberOfDoubleBonds,
            lipidChain.getNumberOfOxygens(), LipidChainType.AMID_CHAIN, msMsScan));
      }
    }
    return matchedFragments;
  }

  protected IMolecularFormula ionizeFragmentBasedOnPolarity(IMolecularFormula formula,
      PolarityType polarityType) {
    if (polarityType.equals(PolarityType.NEGATIVE)) {
      IonizationType.NEGATIVE.ionizeFormula(formula);
      return formula;
    } else if (polarityType.equals(PolarityType.POSITIVE)) {
      IonizationType.POSITIVE.ionizeFormula(formula);
      return formula;
    }
    return formula;
  }

  @NotNull
  protected BestDataPoint getBestDataPoint(Double mzFragmentExact, MassList massList, int index,
      boolean fragmentMatched) {
    int numberOfDataPoints = massList.getNumberOfDataPoints();
    double maxIntensity = 0.0;
    double bestMzValue = 0.0;
    for (int i = index; i < numberOfDataPoints; i++) {
      double intensity = massList.getIntensityValue(i);
      double mzValue = massList.getMzValue(i);
      Range<Double> mzTolRangeMSMS = mzToleranceMS2.getToleranceRange(mzValue);
      if (mzTolRangeMSMS.contains(mzFragmentExact) && intensity > maxIntensity) {
        maxIntensity = intensity;
        bestMzValue = mzValue;
        fragmentMatched = true;
      }
      if (mzTolRangeMSMS.upperEndpoint() < mzValue) {
        break;
      }
    }
    return new BestDataPoint(fragmentMatched, bestMzValue);
  }

  protected record BestDataPoint(boolean fragmentMatched, double mzValue) {

  }

}
