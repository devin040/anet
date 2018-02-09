process.env.NODE_ENV = 'production'

const merge = require('webpack-merge')
const UglifyJSPlugin = require('uglifyjs-webpack-plugin')
const common = require('./webpack.common.js')
const paths = require('../config/paths');
const HtmlWebpackPlugin = require('html-webpack-plugin')

module.exports = merge(common, {
  bail: true,
  output: {
    publicPath: '/assets/client/',
},
  plugins: [
    new UglifyJSPlugin({
      parallel: true,
      cache: true
    }),
    new HtmlWebpackPlugin({
      inject: true,
      template: paths.appHtml,
      filename: paths.appBuild + '/../../views/index.ftl',
      minify: {
          removeComments: true,
          collapseWhitespace: true,
          removeRedundantAttributes: true,
          useShortDoctype: true,
          removeEmptyAttributes: true,
          removeStyleLinkTypeAttributes: true,
          keepClosingSlash: true,
          minifyJS: true,
          minifyCSS: true,
          minifyURLs: true
      }
  })
  ]
})
