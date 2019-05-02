<template>
  <div id="app">
    <label>
      Search {{version}} Mappings:
      <input v-model="query"/>
    </label>
    <table>
      <thead>
      <th class="kind">Kind</th>
      <th>Obfuscated</th>
      <th>Searge</th>
      <th>MCP</th>
      <th>Spigot</th>
      <th>Yarn</th>
      </thead>
      <tbody>
      <tr v-for="mappings of queryEverything">
        <td v-for="mapping in mappings">{{mapping}}</td>
      </tr>
      </tbody>
    </table>
  </div>
</template>
<script>
  /* eslint-disable */
  import fz from 'fuzzaldrin-plus';
  import mappings from '../../mappings/1.14/1.14.json';

  function querySet(query, set, kind) {
    if (!query) return [];

    const preparedQuery = fz.prepareQuery(query);
    const scores = {};

    return set
      .map((mapping) => {
        const scorableFields = Object.values(mapping).map(toScore => fz.score(toScore, query, {preparedQuery}));

        scores[mapping.obf] = Math.max(...scorableFields);

        return mapping;
      })
      .filter(mapping => scores[mapping.obf] > 1)
      .sort((a, b) => scores[b.obf] - scores[a.obf])
      .map(map => Object.assign({kind}, map))
      .map(mapping => [mapping, scores[mapping.obf]])
      .slice(0, 20);
  }

  export default {
    name: 'app',
    data() {
      return {
        query: '',
        version: mappings.minecraftVersion,
        classes: mappings.classes,
        fields: mappings.fields,
        methods: mappings.methods
      }
    },

    computed: {
      queryEverything() {
        const queriedClasses = querySet(this.query, this.classes, "Class");
        const queriedFields = querySet(this.query, this.fields, "Field");
        const queriedMethods = querySet(this.query, this.methods, "Method");


        let combination = [];
        let x = 0;
        for (let j = 0; j < queriedClasses.length; j++) combination[x++] = queriedClasses[j];
        for (let j = 0; j < queriedFields.length; j++) combination[x++] = queriedFields[j];
        for (let j = 0; j < queriedMethods.length; j++) combination[x++] = queriedMethods[j];

        return combination
          .sort((a, b) => b[1] - a[1])
          .map(a => a[0]);
      }
    }
  }
</script>
<style>
  #app {
    font-family: Helvetica, Arial, sans-serif;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
    text-align: center;
    width: 95%;
    margin-left: auto;
    margin-right: auto;
    color: #2c3e50;
  }

  .kind {
    width: 5%;
  }

  table, td, th {
    border: 1px solid #ddd;
    text-align: left;
  }

  table {
    /*table-layout: fixed;*/
    border-collapse: collapse;
    width: 100%;
  }

  th, td {
    padding: 5px;
    /*white-space: nowrap;*/
  }
</style>
