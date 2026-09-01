import { Student, CustomField, FormulaRule } from '../types';

export class FormulaEvaluator {
  static getRawFieldValue(student: Student, fieldKey: string): string {
    switch (fieldKey) {
      case 'id':
        return student.id || '';
      case 'name':
        return student.name || '';
      case 'studentClass':
      case 'class':
        return student.studentClass || '';
      case 'rollNumber':
      case 'roll':
        return student.rollNumber ? student.rollNumber.toString() : '';
      case 'fatherName':
        return student.fatherName || '';
      case 'motherName':
        return student.motherName || '';
      case 'birthDate':
        return student.birthDate || '';
      case 'mobile':
        return student.mobile || '';
      case 'village':
        return student.village || '';
      case 'academicYear':
        return student.academicYear || '';
      case 'address':
        return student.address || '';
      case 'birthRegNumber':
        return student.birthRegNumber || '';
      case 'gender':
        return student.gender || '';
      case 'isSpecialNeeds':
        return student.isSpecialNeeds ? 'হ্যাঁ' : 'না';
      case 'status':
        return student.status || '';
      case 'admissionDate':
        return student.admissionDate || '';
      default:
        return student.customValues?.[fieldKey] || '';
    }
  }

  static evaluateRule(student: Student, rule: FormulaRule): string {
    const rawVal = this.getRawFieldValue(student, rule.sourceField).trim().toLowerCase();
    const condVal = rule.conditionValue.trim().toLowerCase();

    let isMatch = false;

    switch (rule.operator) {
      case 'EQUALS':
        isMatch = rawVal === condVal;
        break;
      case 'NOT_EQUALS':
        isMatch = rawVal !== condVal;
        break;
      case 'CONTAINS':
        isMatch = rawVal.includes(condVal);
        break;
      case 'IN_LIST': {
        const items = condVal.split(',').map(s => s.trim().toLowerCase()).filter(Boolean);
        isMatch = items.some(item => rawVal === item || rawVal.includes(item));
        break;
      }
      case 'GREATER_THAN': {
        const numVal = parseFloat(rawVal);
        const numCond = parseFloat(condVal);
        isMatch = !isNaN(numVal) && !isNaN(numCond) && numVal > numCond;
        break;
      }
      case 'LESS_THAN': {
        const numVal = parseFloat(rawVal);
        const numCond = parseFloat(condVal);
        isMatch = !isNaN(numVal) && !isNaN(numCond) && numVal < numCond;
        break;
      }
      default:
        isMatch = false;
    }

    return isMatch ? rule.resultIfTrue : rule.resultIfFalse;
  }

  static getFieldValue(
    student: Student,
    fieldKey: string,
    customFields: CustomField[],
    formulaRules: FormulaRule[]
  ): string {
    // Check if it's a calculated custom field
    const customField = customFields.find(cf => cf.id === fieldKey || cf.name === fieldKey);
    if (customField && customField.isCalculated && customField.formulaRuleId) {
      const rule = formulaRules.find(r => r.id === customField.formulaRuleId);
      if (rule) {
        return this.evaluateRule(student, rule);
      }
    }

    // Check if matching formula rule exists directly by targetFieldName
    const matchingRule = formulaRules.find(r => r.targetFieldName === fieldKey);
    if (matchingRule) {
      return this.evaluateRule(student, matchingRule);
    }

    return this.getRawFieldValue(student, fieldKey);
  }

  static getStudentCategory(
    student: Student,
    schoolInternalVillages: string = 'পশ্চিম রামপুর,আমতলী,কৃষ্ণপুর'
  ): string {
    if (!student.village) return 'বহিরাগত';
    const villages = schoolInternalVillages.split(',').map(v => v.trim().toLowerCase());
    const studentVill = student.village.trim().toLowerCase();
    const isInternal = villages.some(v => studentVill.includes(v) || v.includes(studentVill));
    return isInternal ? 'অভ্যন্তরীণ' : 'বহিরাগত';
  }
}
