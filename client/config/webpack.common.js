const paths = require('./paths')
const InterpolateHtmlPlugin = require('react-dev-utils/InterpolateHtmlPlugin')
const getClientEnvironment = require('./env')
const ContextReplacementPlugin = require("webpack/lib/ContextReplacementPlugin");
const webpack = require('webpack')
const publicUrl = ''

// Get environment variables to inject into our app.
var env = getClientEnvironment(publicUrl)

module.exports = {
    entry: {
        anet: './src/index.js',
        polyfills: require.resolve('./polyfills')
    },
    // A strange workaround for a strange compile-time bug:   Error in
    // ./~/xmlhttprequest/lib/XMLHttpRequest.js   Module not found: 'child_process'
    // in ./node_modules/xmlhttprequest/lib This fix suggested in:
    // https://github.com/webpack/webpack-dev-server/issues/66#issuecomment-61577531
    externals: [
        {
            xmlhttprequest: '{XMLHttpRequest:XMLHttpRequest}'
        }
    ],
    devtool: 'inline-source-map',
    output: {
        path: paths.appBuild,
        filename: 'static/js/[name].[hash:8].js',
        chunkFilename: 'static/js/[name].[chunkhash:8].chunk.js'
    },
    resolve: {
        modules: [paths.appSrc, "node_modules"]
    },
    module: {
        rules: [
            {
                test: /\.html$/,
                use: 'html-loader'
            }, {
                enforce: "pre",
                test: /\.js$/,
                exclude: /node_modules/,
                loader: "eslint-loader",
                options: {
                    cache: true
                }
            }, {
                test: /\.(js|jsx)$/,
                exclude: /node_modules/,
                use: {
                    loader: 'babel-loader',
                    options: {
                        cacheDirectory: true
                    }
                }
            }, {
                test: /\.css$/,
                use: ["style-loader", "css-loader"]
            }, {
                test: /\.(ico|jpg|jpeg|png|gif|eot|otf|webp|svg|ttf|woff|woff2)(\?.*)?$/,
                use: [
                    {
                        loader: 'file-loader',
                        options: {
                            name: 'static/media/[name].[hash:8].[ext]'
                        }
                    }
                ]
            }
        ]
    },
    plugins: [
        new webpack.optimize.CommonsChunkPlugin({
            name: "dependencies",
            minChunks: ({ resource }) => /node_modules/.test(resource)
          }),
        new ContextReplacementPlugin(/moment[\\\/]locale$/, /^\.\/(en)$/),
        new InterpolateHtmlPlugin({PUBLIC_URL: publicUrl}),
        new webpack.DefinePlugin(env)
    ]
}
