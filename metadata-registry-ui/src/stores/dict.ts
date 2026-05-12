import { defineStore } from 'pinia';
import { ref } from 'vue';

interface LanguageDict {
  id: string;
  languageIdentifier: string;
  scriptIdentifier?: string;
  geopoliticalTerritoryIdentifier?: string;
}

interface SimpleDict {
  id: string;
  identifier: string;
  name?: string;
}

export const useDictStore = defineStore('dict', () => {
  const languages = ref<LanguageDict[]>([]);
  const documentTypes = ref<SimpleDict[]>([]);
  const dataTypeSchemes = ref<SimpleDict[]>([]);
  const dataTypes = ref<SimpleDict[]>([]);
  const namespaces = ref<SimpleDict[]>([]);

  const loading = ref(false);

  return { languages, documentTypes, dataTypeSchemes, dataTypes, namespaces, loading };
});
