(ns river.db)

(def default-db {:active-page   :splashscreen
                 :loading       {:web3 true}
                 :notifications #{}
                 :user          {}
                 :ethers        {}
                 :contracts     {:sablier {:address      "0xc04Ad234E01327b24a831e3718DBFcbE245904CC"
                                           :subgraph-url "https://api.thegraph.com/subgraphs/name/sablierhq/sablier-rinkeby"}
                                 :dai     {:address "0xc3dbf84abb494ce5199d5d4d815b10ec29529ff8"}}})
