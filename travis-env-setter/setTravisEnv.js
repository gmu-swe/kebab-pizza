/**
 * Created by jon on 7/13/17.
 */
var Travis = require('travis-ci');
var travis = new Travis({
    version: '2.0.0',
    headers: {
        'user-agent': 'Travis JS2'
    }
});

var request = require('request');
var RateLimiter = require('limiter').RateLimiter;

var limiter = new RateLimiter(1, 400); // at most 1 request every 100 ms
function setVar(repoId, varName, varVal, token) {
    limiter.removeTokens(1, function () {
        // console.log(repoId);
        request.post(
            'https://api.travis-ci.org/settings/env_vars?repository_id=' + repoId + '&access_token=' + token,
            {
                json: {

                    env_var: {
                        name: varName,
                        value: varVal
                    }
                }
            },
            function (error, response, body) {
                // console.log(response.statusCode);
                if (!error && response.statusCode == 200) {
                    // console.log(body)
                }
                else {
                    console.log("While setting var.." + " " + token);
                    console.log(repoId + ": " + response.statusCode + ". " + response.headers);
                    console.log(body);
                }

            }
        );

    });

}

function setVarInsecure(repoId, varName, varVal, token) {
    limiter.removeTokens(1, function () {
        // console.log(repoId);
        request.post(
            'https://api.travis-ci.org/settings/env_vars?repository_id=' + repoId + '&access_token=' + token,
            {
                json: {

                    env_var: {
                        name: varName,
                        value: varVal,
                        public: "true"
                    }
                }
            },
            function (error, response, body) {
                // console.log(response.statusCode);
                if (!error && response.statusCode == 200) {
                    // console.log(body)
                }
                else {
                    console.log(repoId + ": " + response.statusCode + ". " + response.headers);
                    console.log(body);
                }
                // // console.log(response.headers);
                // console.log(body);
                // console.log(error);
            }
        );

    });

}

function deleteVar(repoId, vid, token) {
    limiter.removeTokens(1, function () {
        // console.log(repoId);
        request.delete(
            'https://api.travis-ci.org/settings/env_vars/' + vid + '?repository_id=' + repoId + '&access_token=' + token,
            function (error, response, body) {
                console.log(response.statusCode);

            }
        );

    });
}

function deleteVars(repoId, token) {
    limiter.removeTokens(1, function () {
        request.get(
            'https://api.travis-ci.org/settings/env_vars?repository_id=' + repoId + '&access_token=' + token,
            function (error, response, body) {
                if (!error && response.statusCode == 200) {

                    var d = JSON.parse(body);
                    for (vi in d.env_vars) {
                        deleteVar(repoId, d.env_vars[vi].id, token);
                    }
                }
                else
                    console.log(repoId + ": " + response.statusCode + ". " + response.headers);

            }
        );

    });
}

function triggerBuild(repoId, token) {
    limiter.removeTokens(1, function () {
        request.post(
            {
                url: "https://api.travis-ci.org/repo/FlakyTestDetection%2F" + repoId + "/requests",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json"
                    , 'Authorization': 'token ' + token,
                    'Travis-API-Version': '3'
                }
            }
            , function (error, response, body) {
                console.log(response.statusCode);
                if (response.statusCode > 250)
                    console.log(body);
            })
    });
}

function checkVars(repoId, token) {
    limiter.removeTokens(1, function () {
        request.get(
            'https://api.travis-ci.org/settings/env_vars?repository_id=' + repoId + '&access_token=' + token,
            function (error, response, body) {
                if (!error && response.statusCode == 200) {

                    var d = JSON.parse(body);
                    var names = {};
                    for (vi in d.env_vars) {
                        names[d.env_vars[vi].name] = 1;
                    }
                    if (!names['FIREBASE_KEY'])
                        console.log(repoId + " missing firebase key");
                }
                else
                    console.log(repoId + ": " + response.statusCode + ". " + response.headers);

            }
        );

    });

}

travis.auth.github.post({
    github_token: "e795ce686b48dc963e91ffe811e948f27415f3b4"
}, function (err, res) {
    console.log(err);
    console.log(res);
    var token = res.access_token;

    var fs = require('fs');
    var array = fs.readFileSync('listOfRepos.csv').toString().split("\n");
    for (repo of array) {
        travis.repos("gmu-swe", repo).get(function (err, res) {
            if (res && res.repo) {
                // checkVars(res.repo.id,res.access_token);
                setVarInsecure(res.repo.id,'KP_RECORD_TESTS','TRUE',token);
                setVarInsecure(res.repo.id,'FIREBASE_HOST','https://kebabpizza-ad088.firebaseio.com/',token);
                setVar(res.repo.id, 'FIREBASE_KEY', '"{\\"type\\": \\"service_account\\",   \\"project_id\\": \\"kebabpizza-ad088\\",   \\"private_key_id\\": \\"45c0e374c76bb66767953a351a789082b63d44d4\\",   \\"private_key\\": \\"-----BEGIN PRIVATE KEY-----\\nMIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCOme4O9ufomWMW\\nHHLvp8kDo6qZNvLGC+vHaI8m5Db5rszm7wdYDgeUprWAzzvTSRhT8ilHyw4fcw64\\nx4zRwFSId67Ql3PXZjqVZ5WZnhYkKMahWmSN3MpKBxWPKI2Va9y/t0hoQdv90NVy\\nEYImrwDLdvWP1ao26QHcC4bV2KpOwbPpxSau1uuEJo94CNREpHDgdfXXJYj/Fxjk\\nTEHzFlObrb47DCtWHgJ6sm0lAhoVbuQGGOCSYI91c5ugcD+mjJg6T5r8IpD094Bo\\nTwX5GBCn4sqTDsy9EXTPB4A8xZQIonwW2NheQLH+mqmXtkiUjIjpvNQs7GEhX6jc\\nvjRc8O3TAgMBAAECggEACXJBrVcYy5CmNaDU9uA22r3vI8ps1Q/eYqR7cgGXumqk\\nbFFHFwzS3ruFPzOo3qtqHxPBYd4Lsz3GmIaLh5nOzXRULcFZf37V7ji8nuNGy8aN\\nJefcXvNkqRzgBjw3jWQKkd6hiH8TzY9xANTGOgbJUnpC9IJ31uM1Q2X/OBkitCgS\\nbHRjcuoc2/sc194m7A2Mjq8Bnuqn11m1WfopruHsSMGVKbA1UHvRtGGBtURSrWz5\\nGnUNrOvod0f4EL9yfuw3faULCxopF60NQDVCoBjTuYuSaiXiE/nuST99SnzLsP7X\\nrtz0gVq5vXJ/mm/JX3PlQt+svE1mrNLzgIZxOd932QKBgQDAM+YHos/OofbU393Y\\nkSmpvATSK1z2nEqbz5o9d4gc0e5zq/FAzdamdB2jpPTQ3RLc7WtdhIbf0gDvbl60\\nQb+tanyU2AfPT5tRhqQ3ktHQ2C3UaZ80JoGXO959lLOVIrz0dHlNNAQWbN+0r9lP\\ncXbST+SIEwgjcM+EbHqetwhtWQKBgQC97zukpD7CDQUNAuqoV6KFFbNSM3xS77Da\\nwcpE4ZN6QwoBAYxNcZBnujjtTpWl4bf06tnBU1SZLFl+eXCXIKi8uVwc5fHYu6du\\nJrWYMyGRt3ZKJxOVqjXZM2BKUyKCPWto5U9vs5NqTvGWWKqhVBI4uQAgs8W/YD7q\\nhnKUZTezCwKBgA/7wONdrL5YRbiKLV63I6nT0urPZFm1wZaXexIlgJI0+wlvgQ6M\\nj48kFNro+trrp4frzoPmY3j1c9J1cTa4SQJY2qdeF7grvJTZB4r/kKc3cwg5CF5D\\n3X8AIY578Tszulbx4KyE6Nocj7Xs8btjiigPMMgIHSMlXHx8sO3F9tNxAoGAPuUn\\ntP+GoK+7CnH6zsAmWzeyZ/gQpU4mHGqr1l90fLoSZ/7hPQvsK80o6LxrJcE3L21N\\nqo0zqH1dLVp9hC4WclhXPmKO0Zxid0FuRPY5mY1NKIUEIT16E3DF5BbfiI1aMiQt\\nR+oJWNzHd2fpKP80AYCRoEKnqxXt/6+B5c4Wwb0CgYB5rEnJ06m4TZvmrTEvbsq2\\nPKUvfVBOe42/ygqo3m1yzjf8eMzO55Ui6Vj3R2raXJnauaZWOmz3nRS+vrUohbnE\\n3CYYD0QBvsZNBj+v7zliUwCV5Pc0hgOiUI6IyzE6tZJXQxF1ya5ipciCEesMOTsf\\ntY47sZlximmcw9s7aKh7Jw==\\n-----END PRIVATE KEY-----\\n\\",   \\"client_email\\": \\"test-publisher@kebabpizza-ad088.iam.gserviceaccount.com\\",   \\"client_id\\": \\"105255110843377243864\\",   \\"auth_uri\\": \\"https://accounts.google.com/o/oauth2/auth\\",   \\"token_uri\\": \\"https://accounts.google.com/o/oauth2/token\\",   \\"auth_provider_x509_cert_url\\": \\"https://www.googleapis.com/oauth2/v1/certs\\",   \\"client_x509_cert_url\\": \\"https://www.googleapis.com/robot/v1/metadata/x509/test-publisher%40kebabpizza-ad088.iam.gserviceaccount.com\\" }"', token);

            }
        });
    }
});
