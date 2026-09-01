export const banglaDigits = ['০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'];
export const englishDigits = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'];

export const banglaMonths = [
  'জানুয়ারি', 'ফেব্রুয়ারি', 'মার্চ', 'এপ্রিল', 'মে', 'জুন',
  'জুলাই', 'আগস্ট', 'সেপ্টেম্বর', 'অক্টোবর', 'নভেম্বর', 'ডিসেম্বর'
];

export const banglaDays = [
  'রবিবার', 'সোমবার', 'মঙ্গলবার', 'বুধবার', 'বৃহস্পতিবার', 'শুক্রবার', 'শনিবার'
];

export function toBanglaDigits(value: any): string {
  if (value === null || value === undefined) return '';
  const str = value.toString();
  return str.split('').map((char: string) => {
    const num = parseInt(char, 10);
    return !isNaN(num) && num >= 0 && num <= 9 ? banglaDigits[num] : char;
  }).join('');
}

export function toEnglishDigits(str: string | null | undefined): string {
  if (!str) return '';
  let result = '';
  for (const ch of str) {
    const idx = banglaDigits.indexOf(ch);
    if (idx !== -1) {
      result += englishDigits[idx];
    } else {
      result += ch;
    }
  }
  return result;
}

export function formatBanglaDate(dateStr: string): string {
  if (!dateStr) return '';
  try {
    const parts = dateStr.split('-');
    if (parts.length === 3) {
      const year = parseInt(parts[0], 10);
      const month = parseInt(parts[1], 10) - 1;
      const day = parseInt(parts[2], 10);
      if (month >= 0 && month < 12) {
        return `${toBanglaDigits(day)} ${banglaMonths[month]}, ${toBanglaDigits(year)}`;
      }
    }
    const d = new Date(dateStr);
    if (!isNaN(d.getTime())) {
      return `${toBanglaDigits(d.getDate())} ${banglaMonths[d.getMonth()]}, ${toBanglaDigits(d.getFullYear())}`;
    }
  } catch (e) {
    // fallback
  }
  return toBanglaDigits(dateStr);
}

export function getTodayDateStr(): string {
  const d = new Date();
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function getDayOfWeekBangla(dateStr: string): string {
  try {
    const d = new Date(dateStr);
    if (!isNaN(d.getTime())) {
      return banglaDays[d.getDay()];
    }
  } catch (e) {
    // fallback
  }
  return 'রবিবার';
}

export interface AgeCalculationResult {
  years: number;
  months: number;
  days: number;
  totalDays: number;
  totalWeeks: number;
  totalMonths: number;
  totalHours: number;
  nextBirthdayDays: number;
  formattedText: string;
}

export function calculateAge(
  startDateStr: string,
  endDateStr: string = getTodayDateStr(),
  includeStartDay: boolean = false,
  includeEndDay: boolean = false
): AgeCalculationResult {
  try {
    const start = new Date(startDateStr);
    const end = new Date(endDateStr);

    if (isNaN(start.getTime()) || isNaN(end.getTime())) {
      return {
        years: 0,
        months: 0,
        days: 0,
        totalDays: 0,
        totalWeeks: 0,
        totalMonths: 0,
        totalHours: 0,
        nextBirthdayDays: 0,
        formattedText: 'অকার্যকর তারিখ'
      };
    }

    let adjustedStart = new Date(start);
    let adjustedEnd = new Date(end);

    // Calculate base difference
    let years = adjustedEnd.getFullYear() - adjustedStart.getFullYear();
    let months = adjustedEnd.getMonth() - adjustedStart.getMonth();
    let days = adjustedEnd.getDate() - adjustedStart.getDate();

    if (includeStartDay) days += 1;
    if (includeEndDay) days += 1;

    if (days < 0) {
      months -= 1;
      const prevMonthLastDay = new Date(adjustedEnd.getFullYear(), adjustedEnd.getMonth(), 0).getDate();
      days += prevMonthLastDay;
    }

    if (months < 0) {
      years -= 1;
      months += 12;
    }

    const diffTime = Math.abs(adjustedEnd.getTime() - adjustedStart.getTime());
    let totalDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    if (includeStartDay) totalDays += 1;
    if (includeEndDay) totalDays += 1;

    const totalWeeks = Math.floor(totalDays / 7);
    const totalMonths = years * 12 + months;
    const totalHours = totalDays * 24;

    // Next Birthday calculation
    const today = new Date();
    let nextBdayYear = today.getFullYear();
    let nextBday = new Date(nextBdayYear, start.getMonth(), start.getDate());
    if (nextBday.getTime() < today.getTime()) {
      nextBday = new Date(nextBdayYear + 1, start.getMonth(), start.getDate());
    }
    const nextBdayDiff = nextBday.getTime() - today.getTime();
    const nextBirthdayDays = Math.max(0, Math.ceil(nextBdayDiff / (1000 * 60 * 60 * 24)));

    const formattedText = `${toBanglaDigits(years)} বছর ${toBanglaDigits(months)} মাস ${toBanglaDigits(days)} দিন`;

    return {
      years: Math.max(0, years),
      months: Math.max(0, months),
      days: Math.max(0, days),
      totalDays,
      totalWeeks,
      totalMonths,
      totalHours,
      nextBirthdayDays,
      formattedText
    };
  } catch (e) {
    return {
      years: 0,
      months: 0,
      days: 0,
      totalDays: 0,
      totalWeeks: 0,
      totalMonths: 0,
      totalHours: 0,
      nextBirthdayDays: 0,
      formattedText: 'গণনা করা যায়নি'
    };
  }
}
